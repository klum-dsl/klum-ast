#!/usr/bin/env bash
# Run from a schema module directory in a clean, disposable Git worktree.
# Do not run this from a multi-module project's top-level directory. Requires Bash and Perl.
set -euo pipefail

git rev-parse --is-inside-work-tree >/dev/null
if [[ -n "$(git status --porcelain)" ]]; then
  echo "Refusing to edit a non-clean worktree; commit, stash, or create a fresh worktree first." >&2
  exit 1
fi
command -v perl >/dev/null || {
  echo "This convenience script requires Perl." >&2
  exit 1
}

roots=()
for candidate in src/main/groovy src/test/groovy src/main/java src/test/java; do
  [[ -d "$candidate" ]] && roots+=("$candidate")
done
(( ${#roots[@]} )) || {
  echo "No conventional Groovy or Java source roots found." >&2
  exit 1
}

# Known public annotation moves, exception imports, and canonical deprecated annotation spelling.
while IFS= read -r -d '' file; do
  perl -0pi -e '
    s{^(\s*import\s+(?:static\s+)?)(com\.blackbuild\.groovy\.configdsl\.transform\.)}{$1com.blackbuild.klum.ast.}mg;
    s{^(\s*import\s+)(com\.blackbuild\.klum\.ast\.util\.layer3\.annotations\.)}{$1com.blackbuild.klum.ast.layer3.}mg;
    s{^(\s*import\s+)(com\.blackbuild\.klum\.ast\.util\.copy\.)}{$1com.blackbuild.klum.ast.copy.}mg;
    s{^(\s*import\s+)(com\.blackbuild\.klum\.ast\.util\.KlumModelException\b)}{$1com.blackbuild.klum.ast.runtime.KlumModelException}mg;
    s{^(\s*import\s+)(com\.blackbuild\.klum\.ast\.util\.KlumValidationException\b)}{$1com.blackbuild.klum.ast.runtime.validation.KlumValidationException}mg;
    s{^(\s*import\s+)(com\.blackbuild\.klum\.ast\.runtime\.KlumValidationException\b)}{$1com.blackbuild.klum.ast.runtime.validation.KlumValidationException}mg;
    s{\bDelegatesToRW\b}{DelegatesToBuilder}g;
  ' "$file"
done < <(find "${roots[@]}" -type f \( -name '*.groovy' -o -name '*.java' \) -print0)

# Current-target Groovy Validator calls only. Explicit-target calls, ValidatorBase,
# and validation-result readers deliberately remain for manual migration.
groovy_roots=()
for candidate in src/main/groovy src/test/groovy; do
  [[ -d "$candidate" ]] && groovy_roots+=("$candidate")
done

if (( ${#groovy_roots[@]} )); then
  while IFS= read -r -d '' file; do
    if grep -qE 'Validator\.(addError|addErrorToMember|addIssue|addIssueToMember|suppressFurtherIssues)' "$file"; then
    perl -0pi -e '
      s{Validator\.suppressFurtherIssues\(\s*Validator\.ANY_MEMBER\s*,\s*([^\)]+)\)}{klumValidation.suppressAll($1)}g;
      s{Validator\.suppressFurtherIssues\(\s*Validator\.ANY_MEMBER\s*\)}{klumValidation.suppressAll()}g;
      s{\bValidator\.addErrorToMember\b}{klumValidation.errorAt}g;
      s{\bValidator\.addError\b}{klumValidation.error}g;
      s{\bValidator\.addIssueToMember\b}{klumValidation.issueAt}g;
      s{\bValidator\.addIssue\b}{klumValidation.issue}g;
      s{\bValidator\.suppressFurtherIssues\b}{klumValidation.suppressOn}g;
      if (!/Validator\./) {
        s{^\s*import\s+com\.blackbuild\.klum\.ast\.validation\.Validator\s*\n}{}mg;
      }
      if (/\bklumValidation\./ && !/import\s+static\s+com\.blackbuild\.klum\.ast\.runtime\.KlumSchemaSupport\.klumValidation/) {
        if (!s{^(package[^\n]*\n)}{$1\nimport static com.blackbuild.klum.ast.runtime.KlumSchemaSupport.klumValidation\n}m) {
          s{\A}{import static com.blackbuild.klum.ast.runtime.KlumSchemaSupport.klumValidation\n\n};
        }
      }
    ' "$file"
    fi
  done < <(find "${groovy_roots[@]}" -type f -name '*.groovy' -print0)
fi

echo "Starter edits applied. Review 'git diff', then compile, run a representative model, and follow Builder-First-Migration.md."

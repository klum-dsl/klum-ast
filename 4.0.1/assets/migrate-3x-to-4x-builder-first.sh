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

# Template creation has moved below Create. Restrict this to direct, type-qualified
# calls and leave comments and quoted/script content untouched. The scanner is
# deliberately conservative: an unfamiliar slash expression is treated as opaque.
rewrite_template_entrypoints() {
  perl -0pi -e '
    sub rewrite_code {
      my ($code) = @_;
      $code =~ s{(?<![\w\$\.])([A-Z]\w*(?:\.[A-Z]\w*)*)\.Create\.TemplateFrom(\s*\()}{${1}.Create.Template.From$2}g;
      $code =~ s{(?<![\w\$\.])([A-Z]\w*(?:\.[A-Z]\w*)*)\.Create\.Template(\s*[\(\{])}{${1}.Create.Template.With$2}g;
      $code =~ s{(?<![\w\$\.])([A-Z]\w*(?:\.[A-Z]\w*)*)\.Template\.CreateFrom(\s*\()}{${1}.Create.Template.From$2}g;
      $code =~ s{(?<![\w\$\.])([A-Z]\w*(?:\.[A-Z]\w*)*)\.Template\.Create(\s*[\(\{])}{${1}.Create.Template.With$2}g;
      return $code;
    }

    sub quoted_end {
      my ($text, $start, $quote) = @_;
      my $length = length $text;
      my $index = $start + 1;
      while ($index < $length) {
        return $index + 1 if substr($text, $index, 1) eq $quote;
        $index += substr($text, $index, 1) eq q{\\} ? 2 : 1;
      }
      return $length;
    }

    my $text = $_;
    my $triple_single = chr(39) x 3;
    my $triple_double = chr(34) x 3;
    my ($out, $code) = (q{}, q{});
    my ($index, $length) = (0, length $text);
    while ($index < $length) {
      my $tail = substr($text, $index);
      my ($delimiter, $end);
      if ($tail =~ m{\A//}) {
        $delimiter = "\n";
        $end = index($text, $delimiter, $index + 2);
        $end = $length if $end < 0;
        $end++ if $end < $length;
      } elsif ($tail =~ m{\A/\*}) {
        $end = index($text, "*/", $index + 2);
        $end = $length if $end < 0;
        $end += 2 if $end < $length;
      } elsif ($tail =~ m{\A\$/}) {
        $end = index($text, "/\$", $index + 2);
        $end = $length if $end < 0;
        $end += 2 if $end < $length;
      } elsif (substr($text, $index, 3) eq $triple_single || substr($text, $index, 3) eq $triple_double) {
        $delimiter = substr($text, $index, 3);
        $end = index($text, $delimiter, $index + 3);
        $end = $length if $end < 0;
        $end += 3 if $end < $length;
      } elsif ($tail =~ m{\A[\x27\x22\x60]}) {
        $end = quoted_end($text, $index, substr($text, $index, 1));
      } elsif ($tail =~ m{\A/}) {
        $end = quoted_end($text, $index, "/");
      } else {
        $code .= substr($text, $index, 1);
        $index++;
        next;
      }
      $out .= rewrite_code($code) . substr($text, $index, $end - $index);
      $code = q{};
      $index = $end;
    }
    $_ = $out . rewrite_code($code);
  ' "$1"
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

# Known Template creation entrypoints. This leaves scoped Template.With/WithAll,
# variables, dynamic calls, method references, comments, and script text alone.
while IFS= read -r -d '' file; do
  rewrite_template_entrypoints "$file"
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

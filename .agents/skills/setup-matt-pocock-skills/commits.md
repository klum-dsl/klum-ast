# Issue branches and commit history

Use these rules when implementing an issue.

## Work on a dedicated branch

- Create a new branch dedicated to the current issue before the first commit. Reuse the current branch only when it was newly created for this issue. Follow an established repository naming convention when one exists; otherwise include the issue identifier and a short topic.
- A dedicated issue branch contains only work for that issue and starts from the agreed base revision.
- Once on that new issue branch, create and amend commits without asking. On an existing, shared, or unrelated branch, obtain permission before committing.
- Preserve unrelated worktree changes and keep them out of the issue history.

## Tell the reasoning in small commits

- Make each commit one self-contained reasoning step: a reviewer should be able to understand why the step exists and how it advances the issue without depending on unrelated later work.
- Prefer small vertical or preparatory steps over commits split mechanically by file or layer.
- Write a concise imperative subject that states the change and its reason. Use the body only when the reasoning does not fit clearly in the subject.
- Keep tests and the production change that makes them pass together. During TDD, do not commit the initial failing test separately; finish the red-to-green cycle and commit only when that test is green again.
- Before each commit, run the relevant build and tests and normally require them to pass. Outside a TDD cycle, a temporarily non-working intermediate commit is acceptable when it makes the reasoning materially easier to follow; repair it in the immediately following commit and explain the boundary in both messages.
- Documentation need not describe every intermediate commit. It must match the final branch state unless the issue explicitly excludes documentation. A separate, usually final documentation commit is valid.

## Review the history before handoff

After implementation and code review, inspect the commits from the issue branch base through `HEAD` against the issue and the final diff.

- Check that each commit is focused, ordered by reasoning and dependency, and explained by its message.
- Combine commits whose separation adds no comprehension, split mixed-purpose commits, reorder dependent steps, and reword unclear messages.
- Rewrite the local issue-branch history without asking when these changes improve it. Complete this review before publishing or handing off the branch so shared history is not rewritten implicitly.
- Re-run the appropriate build and tests on the rewritten final tip. The branch is complete only when the final code, tests, and required documentation are mutually consistent and every acceptance criterion still has evidence.

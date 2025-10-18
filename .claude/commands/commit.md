
Create a git commit following project standards

Include any of the following info if specified: $ARGUMENTS

## Commit Message Rules

1. **Format**: `type(#issue): description`

   - Use `#123` for local repo issues
   - Use `kalix/#123` for cross-repo issues

2. **AI Credits**: **NEVER include AI credits in commit messages**

   - No "Generated with Claude Code"
   - No "Co-Authored-By: Claude" or "Co-Authored-By: Happy"
   - Focus on the actual changes made, not conversation history

3. **Content**: Write clear, concise commit messages describing what changed and why

## Process

1. Run `git status` and `git diff` to review changes
2. Run `git log --oneline -5` to see recent commit style
3. Stage relevant files with `git add`
4. Create commit with descriptive message
5. Verify with `git status`

## Example

```bash
git add <files>
git commit -m "feat(kalix/#14342): add minimum width to timeline elements"
```

# Contributing to Opspect

Thank you for your interest in contributing to Opspect.

## How to Contribute

- **Check for Issues:** Before starting work, please search the Issue Tracker to see if someone else is already working on it.
- **Sign the DCO:** All contributors must sign the Developer Certificate of Origin (DCO). This is done by adding a `Signed-off-by` line to every commit message. Use `git commit -s` to automatically add this.
- **Fork & Branch:** Fork the repo and create your branch from `main`.
- **Submit a PR:** Open a Pull Request with a clear description of the changes and link to any relevant issues.

## Signing the DCO

All commits must include a `Signed-off-by` line to certify that you comply with the Developer Certificate of Origin (DCO).

### Using git commit -s

The easiest way is to use the `-s` flag:

```sh
git commit -s -m "Your commit message"
```

This automatically adds a line like:
```
Signed-off-by: Your Name <your.email@example.com>
```

### Configuring your name and email

Make sure your git config is set correctly:

```sh
git config user.name "Your Name"
git config user.email "your.email@example.com"
```

### Verifying your commits

To verify your commits have the signed-off line:

```sh
git log --format="%s %b" | grep "Signed-off-by"
```

Or use the DCO GitHub App to automatically check commits on pull requests.

## Development Setup

- Clone the repo:
  ```sh
  git clone https://github.com/<your-fork>/opspect.git
  cd opspect
  ```

- Build the project:
  ```sh
  go build -o opspect main.go
  ```

- Run the application:
  ```sh
  ./opspect -config <config-file>
  ```

## Coding Standards

- Run `go fmt` before committing
- Follow standard Go conventions
- Ensure code is properly formatted and linted

## Testing

TODO: Add testing instructions

## Reporting Bugs

- Use the Issue Tracker to report bugs
- Include steps to reproduce, expected vs actual behavior
- Include relevant configuration and environment details

## License

This project is licensed under AGPLv3. See the [LICENSE](LICENSE) file for details.

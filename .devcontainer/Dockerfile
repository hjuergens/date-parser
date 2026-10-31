# Development container for date-parser: system packages only.
# The toolchains (JDK, Maven, Node, Python, uv) come from mise.toml via `mise install` (postCreateCommand),
# so their versions are pinned in one place.
FROM mcr.microsoft.com/devcontainers/base:1-bookworm

# bison (with m4) for date-rule-bison: /usr/bin/bison activates its Maven profile
RUN apt-get update \
 && apt-get install -y --no-install-recommends bison \
 && rm -rf /var/lib/apt/lists/*

# mise as a pinned release binary, checked against the release's SHASUMS256.txt
# (https://github.com/jdx/mise/releases); no apt repository whose mirror sync can break the build
ARG MISE_VERSION=2026.10.19
RUN case "$(dpkg --print-architecture)" in \
      amd64) arch=x64;   sha=3162760f40b6b5f706c60641e4ef5ee7322bc6c54f72fc26a7111b337aa18d9d ;; \
      arm64) arch=arm64; sha=4a4ca53b28e5433c527f4f73fac1f98f992a17cf32ca136a58e2e375f790f627 ;; \
      *) echo "no mise binary for $(dpkg --print-architecture)" >&2; exit 1 ;; \
    esac \
 && curl -fsSL -o /usr/local/bin/mise \
      "https://github.com/jdx/mise/releases/download/v${MISE_VERSION}/mise-v${MISE_VERSION}-linux-${arch}" \
 && echo "${sha}  /usr/local/bin/mise" | sha256sum -c - \
 && chmod 755 /usr/local/bin/mise

# mount points of the volumes in devcontainer.json; created here, Docker would create their parents as root
RUN install -d -o vscode -g vscode /home/vscode/.local /home/vscode/.local/share /home/vscode/.local/share/mise \
      /home/vscode/.local/state /home/vscode/.m2 /home/vscode/.cache /home/vscode/.cache/uv

# mise's shims put the pinned tools on the PATH, also for non-interactive shells (tasks, VS Code extensions)
ENV PATH="/home/vscode/.local/share/mise/shims:${PATH}"
RUN echo 'eval "$(mise activate bash)"' >> /home/vscode/.bashrc

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
ARG MISE_VERSION=2026.10.10
RUN case "$(dpkg --print-architecture)" in \
      amd64) arch=x64;   sha=7d79972e15549b286dcf9e25ef8c4fcef3d9fd7c0332d11cdfbc7d2842c418be ;; \
      arm64) arch=arm64; sha=3978fbccfa2284b12e46cbf6df53cae118ec1f2fa780b6a204e6c9c792b620f6 ;; \
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

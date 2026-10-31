# Development container for date-parser: system packages only.
# The toolchains (JDK, Maven, Node, Python, uv) come from mise.toml via `mise install` (postCreateCommand),
# so their versions are pinned in one place.
FROM mcr.microsoft.com/devcontainers/base:1-bookworm

# bison (with m4) for date-rule-bison: /usr/bin/bison activates its Maven profile
# mise from its signed apt repository (https://mise.jdx.dev/installing-mise.html#apt)
RUN apt-get update \
 && apt-get install -y --no-install-recommends bison gpg \
 && install -dm 755 /etc/apt/keyrings \
 && wget -qO - https://mise.jdx.dev/gpg-key.pub | gpg --dearmor > /etc/apt/keyrings/mise-archive-keyring.gpg \
 && echo "deb [signed-by=/etc/apt/keyrings/mise-archive-keyring.gpg arch=$(dpkg --print-architecture)] https://mise.jdx.dev/deb stable main" \
      > /etc/apt/sources.list.d/mise.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends mise \
 && rm -rf /var/lib/apt/lists/*

# mount points of the volumes in devcontainer.json; created here, Docker would create their parents as root
RUN install -d -o vscode -g vscode /home/vscode/.local /home/vscode/.local/share /home/vscode/.local/share/mise \
      /home/vscode/.local/state /home/vscode/.m2 /home/vscode/.cache /home/vscode/.cache/uv

# mise's shims put the pinned tools on the PATH, also for non-interactive shells (tasks, VS Code extensions)
ENV PATH="/home/vscode/.local/share/mise/shims:${PATH}"
RUN echo 'eval "$(mise activate bash)"' >> /home/vscode/.bashrc

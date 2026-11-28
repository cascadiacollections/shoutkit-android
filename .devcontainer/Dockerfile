FROM mcr.microsoft.com/devcontainers/java:25@sha256:ba69d01f9f399529047736a1fe541ea1c7e783be726c362be2b91d4e125d928a

ARG ANDROID_SDK_VERSION=11076708
ARG ANDROID_BUILD_TOOLS=37.0.0
ARG ANDROID_PLATFORM=android-37.0

ENV ANDROID_HOME=/usr/local/android-sdk
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

# Install Android command-line tools
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_SDK_VERSION}_latest.zip" -o /tmp/cmdtools.zip \
    && unzip -q /tmp/cmdtools.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm /tmp/cmdtools.zip

# Accept licenses, install SDK components, and grant ownership to vscode
RUN (yes || true) | sdkmanager --licenses > /dev/null \
    && sdkmanager --install \
        "platforms;${ANDROID_PLATFORM}" \
        "build-tools;${ANDROID_BUILD_TOOLS}" \
        "platform-tools" \
    && chown -R vscode:vscode ${ANDROID_HOME}

FROM ubuntu:22.04

ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    build-essential \
    curl \
    git \
    wget \
    unzip \
    zip \
    ca-certificates \
    gnupg \
    lsb-release \
    apt-transport-https \
    software-properties-common \
    sudo \
    vim \
    nano \
    htop \
    tmux \
    && rm -rf /var/lib/apt/lists/*

RUN add-apt-repository ppa:openjdk-r/ppa && \
    apt-get update && \
    apt-get install -y openjdk-25-jdk && \
    rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

RUN useradd -m -s /bin/bash -G sudo devcontainer && \
    echo "devcontainer ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

USER devcontainer

RUN mkdir -p /home/devcontainer/.gradle /home/devcontainer/.m2 /home/devcontainer/Android/sdk

RUN curl -s https://get.sdkman.io | bash && \
    bash -c "source $HOME/.sdkman/bin/sdkman-init.sh && \
    sdk install kotlin && \
    sdk install gradle"

ENV PATH=/home/devcontainer/.sdkman/candidates/kotlin/current/bin:/home/devcontainer/.sdkman/candidates/gradle/current/bin:$PATH

# Install Android SDK
ENV ANDROID_HOME=/home/devcontainer/Android/sdk
ENV ANDROID_SDK_ROOT=$ANDROID_HOME
ENV PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools/bin:$PATH

RUN mkdir -p $ANDROID_HOME/cmdline-tools && \
    cd $ANDROID_HOME/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip -q commandlinetools-linux-11076708_latest.zip && \
    rm commandlinetools-linux-11076708_latest.zip && \
    mv cmdline-tools latest

RUN yes | sdkmanager --licenses && \
    sdkmanager "platform-tools" \
    "platforms;android-34" \
    "build-tools;34.0.0" \
    "ndk;26.1.10909125"

WORKDIR /workspace

RUN kotlinc -version && gradle --version && sdkmanager --version

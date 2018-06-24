FROM ubuntu:16.04

ENV DEBIAN_FRONTEND noninteractive

RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true\
    | debconf-set-selections
RUN apt update &&\
    apt upgrade -y &&\
    apt install -y software-properties-common git unzip wget vim &&\
    add-apt-repository -y ppa:webupd8team/java &&\
    apt update &&\
    apt install -y oracle-java8-installer &&\
    apt clean &&\
    rm -rf /var/lib/apt/lists/* /var/cache/oracle-jdk8-installer
RUN wget https://dl.google.com/android/android-sdk_r24.4.1-linux.tgz &&\
    tar xf android-sdk_r24.4.1-linux.tgz &&\
    mv android-sdk-linux /usr/local/android-sdk &&\
    rm android-sdk_r24.4.1-linux.tgz
RUN echo y | /usr/local/android-sdk/tools/android update sdk --filter "platform-tools,android-27,build-tools-27.0.3,tools,extra-android-m2repository,extra-google-m2repository,extra-google-google_play_services" --no-ui -a
RUN wget https://services.gradle.org/distributions/gradle-4.4-bin.zip &&\
    unzip gradle-4.4-bin.zip &&\
    mv gradle-4.4/bin/* /usr/bin/ &&\
    mv gradle-4.4/lib/* /usr/lib/ &&\
    rm -rf gradle-4.4*


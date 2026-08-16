# Boquila

Boquila is a desktop application for managing and sharing reports between developers.

## Installation

### Windows

To install Boquila on Windows, download and run the provided `.exe` installer.

The installer handles the installation and application setup automatically.

**Windows Installer:**

<a href="https://drive.proton.me/urls/F4RR5SBZ98#74PTUhoFY8Ql" target="_blank">
  <img
    src="https://img.shields.io/badge/Download%20Windows%20Installer-0078D4?style=for-the-badge&logo=windows&logoColor=white"
    alt="Download Windows Installer"/>
</a>

---

### Linux

On Linux, Boquila is distributed as a `.deb` package.

The package can be built directly from the source code using the provided build script.

## Linux Prerequisites

Before building Boquila on Linux, make sure the following dependencies are installed:

* Git
* OpenJDK 21
* Maven
* `jpackage`
* `fakeroot`
* `dpkg`

On Ubuntu/Debian-based systems, install the required packages with:

```bash
sudo apt update
sudo apt install git maven openjdk-21-jdk fakeroot dpkg
```

### Verify the Prerequisites

Check that Java is available:

```bash
java -version
```

Check the Java compiler:

```bash
javac -version
```

Check Maven:

```bash
mvn -version
```

Check `jpackage`:

```bash
jpackage --version
```

Check `fakeroot`:

```bash
fakeroot --version
```

Check `dpkg`:

```bash
dpkg --version
```

`jpackage` is included with the JDK, so make sure the system is using JDK 21 or newer.

If multiple Java versions are installed, select JDK 21 as the default:

```bash
sudo update-alternatives --config java
sudo update-alternatives --config javac
```

Then verify:

```bash
java -version
jpackage --version
```

---

## Building Boquila on Linux

Clone the repository:

```bash
git clone <REPOSITORY_URL>
cd <REPOSITORY_DIRECTORY>
```

Run the Linux build script:

```bash
sudo bash ./scripts/build-linux.sh
```

The build process will perform the following steps:

```text
== Build jar ==
== Prepare staging dir ==
== Ensure dpkg tooling ==
== jpackage --type deb ==
```

If the build completes successfully, the `.deb` package will be generated in:

```text
target/dist/Boquila_1.0.0_amd64.deb
```

The generated `.deb` file is the Linux installation package.

---

## Installing the Linux Package

After building the package, install it with:

```bash
sudo dpkg -i target/dist/Boquila_1.0.0_amd64.deb
```

---

## Troubleshooting

### `Missing required tool: mvn`

Maven is not installed or is not available in the system `PATH`.

Install Maven:

```bash
sudo apt update
sudo apt install maven
```

Verify the installation:

```bash
mvn -version
```

---

### `Missing required tool: jpackage`

This usually means that an older JDK is installed.

For example, JDK 11 does not provide the required `jpackage` tool.

Install JDK 21:

```bash
sudo apt update
sudo apt install openjdk-21-jdk
```

Then verify:

```bash
java -version
jpackage --version
```

If multiple JDK versions are installed, select JDK 21:

```bash
sudo update-alternatives --config java
sudo update-alternatives --config javac
```

---

### `Can not find fakeroot`

If the build fails with:

```text
Can not find fakeroot
```

install `fakeroot`:

```bash
sudo apt update
sudo apt install fakeroot
```

Verify:

```bash
fakeroot --version
```
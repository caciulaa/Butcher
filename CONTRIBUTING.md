# Building Butcher yourself

You can build the mod from this source for your own game. It takes one command and a few minutes.

## What you need

- **A Java Development Kit (JDK), version 21 or newer.** [Adoptium](https://adoptium.net/) has free installers. The game's
  own Java isn't enough, because it can't compile anything. To check, run `java -version` in a terminal.
- **The source.** Click **Code → Download ZIP** at the top of this page and unzip it, or use
  `git clone https://github.com/caciulaa/butcher.git`.

You don't need to install Gradle or Java 25: the project downloads both the first time you build.

## Build it

Open a terminal in the folder with `build.gradle` in it, then run:

**Windows**

```
.\gradlew.bat build
```

**Linux / macOS**

```
./gradlew build
```

The first build downloads Minecraft and the tools it needs: about 1 GB, and a few minutes on a good connection. It's done
when it says `BUILD SUCCESSFUL`.

## Install it

1. Your jar is in `build/libs/`. Use `butcher-<version>.jar`, not the one ending in `-sources`.
2. Put it in your `mods` folder, next to [Fabric API](https://modrinth.com/mod/fabric-api). You need
   [Fabric Loader](https://fabricmc.net/use/installer/) for **Minecraft 26.2**.

## If it goes wrong

| What you see | What to do |
| --- | --- |
| `JAVA_HOME is not set and no 'java' command could be found` | Install a JDK, then open a new terminal. |
| `Gradle requires JVM 17 or later`, or another message about your Java version | Your JDK is too old. Install version 21 or newer, then open a new terminal. |
| `Permission denied` on Linux or macOS | Run `chmod +x gradlew`, then build again. |
| It stops on a download error | Check your connection and run the same command again. |

## What you can do with your build

It's for your own game. You can't share the jar or build changed versions without asking first: see the
[license](LICENSE).

Found a bug or have an idea? Run `/butcher report` in game, or open an
[issue](https://github.com/caciulaa/butcher/issues).

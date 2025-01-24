[![Logisim-evolution](docs/img/logisim-evolution-logo.png)](https://github.com/logisim-evolution/logisim-evolution)

---

# Logisim-evolution-edu #

This fork of `Logisim-evolution` aims to enhance the educational features of the software. The primary objective is to provide additional tools and functionalities to assist in the simplification of Boolean algebra expressions. Key changes include:

- New GUI for Boolean expression simplification.
- Algorithms for help in Boolean expression simplification.

These changes are designed to make `Logisim-evolution` more useful for educational settings, particularly in teaching and learning digital logic design and Boolean algebra.

---

* **Table of contents**
  * [Features](#features)
  * [Requirements](#requirements)
  * [Downloads](#download)
  * [Pictures of Logisim-evolution](docs/pics.md)
  * [More Information](docs/docs.md)
  * [Bug reports & feature requests](https://github.com/logisim-evolution/logisim-evolution/issues)
  * [For developers](docs/developers.md)
  * [How to contribute](docs/developers.md#how-to-contribute)
  * [Credits](docs/credits.md)

---

## Features ##

`Logisim-evolution` is educational software for designing and simulating digital logic circuits.
`Logisim-evolution` is [free](#license), [open-source](https://github.com/logisim-evolution), and [cross-platform](#requirements).

Project highlights:

* easy to use circuit designer,
* logic circuit simulations,
* chronogram (to see the evolution of signals in your circuit),
* electronic board integration (schematics can be simulated on real hardware),
* VHDL components (components behavior can be specified in VHDL!),
* TCL/TK console (interfaces between the circuit and the user),
* huge library of components (LEDs, TTLs, switches, SoCs),
* supports [multiple languages](docs/docs.md#translations),
* and more!

[![Logisim-evolution](docs/img/logisim-evolution-01-small.png)](docs/pics.md)
[![Logisim-evolution](docs/img/logisim-evolution-02-small.png)](docs/pics.md)

---

## Requirements ##

`Logisim-evolution` is a Java application; therefore, it can run on any operating system supporting the Java runtime enviroment.
It requires [Java 16 (or newer)](https://www.oracle.com/java/technologies/javase-downloads.html).

---

## Download ###

Clone the repo and run the `logisim.jar` file in the `Compiled` folder.

To run the file in the command line just run:
`java -jar logisim.jar`

---

**Note for macOS users**:
The Logisim-evolution.app is not signed with an Apple approved certificate.

When launching the application for the first time, you will have to start it via the "Open" entry in the
application icon's context menu in the macOS Finder. This is either done by clicking the application
icon with the right mouse button or holding down <kbd>CTRL</kbd> while clicking the icon with the
left mouse button. This will open a panel asking you to verify that you wish to launch the application.
On more recent versions of macOS, the panel will only give you a choice of moving the app to the trash or Cancel.
On those systems, click Cancel, open `System Preferences`, and select `Security & Privacy`.
There you may need to click the lock to make changes and authenticate with an administrative acccount.
It should show an option to open the app.
See [Safely open apps on your Mac](https://support.apple.com/en-us/HT202491) for more information.

Depending on your security settings, you may also get a panel asking if you wish to allow it to accept
network connections. You can click "Deny" as we do not need network access currently nor we do request any.

### Nightly builds ###

We also offer builds based on the current state of the
[develop](https://github.com/logisim-evolution/logisim-evolution/tree/develop) branch.
If the develop branch has been changed,
a new `Nightly build` is created at midnight [UTC](https://en.wikipedia.org/wiki/Coordinated_Universal_Time).

Note that these builds may be unstable since the develop branch is a work in progress.

To get nightly downloads, please
[click here](https://github.com/logisim-evolution/logisim-evolution/actions/workflows/nightly.yml)
and browse to the last successful run of `Nightly build`, which should be on top. Note that due to Github internals,
all files are provided as ZIP archives. You must unzip the downloaded file to get the package for installation.

Please share your experience in [Discussions](https://github.com/logisim-evolution/logisim-evolution/discussions)
or [open a ticket](https://github.com/logisim-evolution/logisim-evolution/issues)
if you found a bug or have suggestions for improvement.

---

## License ##

* `Logisim-evolution` is copyrighted ©2001-2022 by Logisim-evolution [developers](docs/credits.md).
* This is free software licensed under [GNU General Public License v3](https://www.gnu.org/licenses/gpl-3.0.en.html).

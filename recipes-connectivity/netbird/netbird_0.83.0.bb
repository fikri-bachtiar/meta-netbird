SUMMARY = "NetBird WireGuard mesh VPN client (official prebuilt static binary)"
DESCRIPTION = "Installs the official NetBird release binary, netbird-autostart \
(netbird service run + netbird up with retries) as a systemd service, and the \
settings from files/autostart.env."
HOMEPAGE = "https://netbird.io"
SECTION = "net"
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/BSD-3-Clause;md5=550794465ba0ec5312d6919e203a55f9"

# Yocto TARGET_ARCH -> NetBird release arch
NB_ARCH = "unsupported"
NB_ARCH:aarch64 = "arm64"
NB_ARCH:arm = "armv6"
NB_ARCH:x86-64 = "amd64"
COMPATIBLE_HOST = "(aarch64|arm|x86_64).*-linux.*"

SRC_URI = "https://github.com/netbirdio/netbird/releases/download/v${PV}/netbird_${PV}_linux_${NB_ARCH}.tar.gz;subdir=${BP};name=${NB_ARCH} \
           file://netbird-autostart \
           file://netbird.service \
           file://99-netbird.conf \
           file://autostart.env \
"
# From netbird_${PV}_checksums.txt on the GitHub release
SRC_URI[arm64.sha256sum] = "9450ef96f3ede090efe175e08f7c53a356bc7ada454e7c755f99050bd1ebebb7"
SRC_URI[armv6.sha256sum] = "89a4ac6d57c5c31421645b39ce6483f0632b5f3fe85690a5b5ac829ea63efeb6"
SRC_URI[amd64.sha256sum] = "50911d0c9482b91523169245241d3453edbed5d0cc23ff64003ca95a7e5e2e96"

# Dunfell..Scarthgap unpack into WORKDIR, Styhead and later into UNPACKDIR
NB_SRCDIR = "${@d.getVar('UNPACKDIR') or d.getVar('WORKDIR')}"
S = "${NB_SRCDIR}/${BP}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "netbird.service"
SYSTEMD_AUTO_ENABLE = "enable"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -Dm 0755 ${S}/netbird ${D}${bindir}/netbird
    install -Dm 0755 ${NB_SRCDIR}/netbird-autostart ${D}${bindir}/netbird-autostart
    install -Dm 0644 ${NB_SRCDIR}/netbird.service ${D}${systemd_system_unitdir}/netbird.service
    # Same drop-in "netbird service install" writes: stops systemd-networkd
    # from deleting NetBird's routes. Harmless without networkd.
    install -Dm 0644 ${NB_SRCDIR}/99-netbird.conf ${D}${sysconfdir}/systemd/networkd.conf.d/99-netbird.conf

    # Settings + setup key: copy files/autostart.env.example to autostart.env
    install -d -m 0700 ${D}${sysconfdir}/netbird
    install -m 0600 ${NB_SRCDIR}/autostart.env ${D}${sysconfdir}/netbird/autostart.env
    if ! grep -q '^NB_SETUP_KEY' ${NB_SRCDIR}/autostart.env; then
        bbwarn "autostart.env has no NB_SETUP_KEY: the image will not enrol until a key is provisioned"
    fi
}

FILES:${PN} += "${sysconfdir}/netbird ${sysconfdir}/systemd/networkd.conf.d"
CONFFILES:${PN} = "${sysconfdir}/netbird/autostart.env"

# Prebuilt static Go binary: leave it alone
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"
INSANE_SKIP:${PN} += "already-stripped ldflags"

# Only pulled in when TUN / WireGuard are built as modules
RRECOMMENDS:${PN} += "kernel-module-tun kernel-module-wireguard"

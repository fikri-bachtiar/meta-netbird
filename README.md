# meta-netbird

Yocto layer that adds the [NetBird](https://netbird.io) client to an image and
starts it on every boot.

NetBird is an open-source mesh VPN built on WireGuard. Each device runs a
small client that registers with a NetBird management server (the hosted
service or your own) using a setup key, then connects directly and encrypted
to the other peers in your network, through NAT and firewalls, without
opening ports. Once a device is up, you can reach it by its NetBird IP or name
from any other peer.

Board-independent: works with any MACHINE on aarch64, 32-bit ARM or x86-64
with systemd. The recipes parse on Dunfell through Walnascar.

## Layout

```
meta-netbird/
├── conf/layer.conf              # also adds netbird.cfg to the MACHINE's kernel
├── recipes-connectivity/netbird/
│   ├── netbird_0.83.0.bb        # binary + autostart + service + settings
│   ├── .gitignore               # keeps files/autostart.env (setup key) out of git
│   └── files/
│       ├── autostart.env.example # template -> copy to autostart.env
│       ├── autostart.env        # YOUR settings + setup key (git-ignored)
│       ├── netbird-autostart    # daemon + "netbird up" with retries, re-login watch
│       ├── netbird.service      # systemd unit (Restart=always)
│       └── 99-netbird.conf      # systemd-networkd: keep NetBird's routes
└── recipes-kernel/linux/
    └── files/netbird.cfg        # CONFIG_TUN=y kernel fragment
```

## Use

### Option A: copy into your own layer (simplest)

1. Copy `recipes-connectivity/netbird/` (the `.bb` and `files/`) into any
   layer you already build, e.g. `meta-mylayer/recipes-connectivity/netbird/`.
2. Kernel: make sure your kernel has `CONFIG_TUN=y`, e.g. add it to a
   fragment your kernel bbappend already uses, or copy
   `recipes-kernel/linux/files/netbird.cfg` next to it and add
   `SRC_URI += "file://netbird.cfg"` to that bbappend.

### Option B: use this as a layer

```sh
bitbake-layers add-layer ../meta-netbird
```

The layer adds `netbird.cfg` (`CONFIG_TUN=y`) to whatever kernel the MACHINE
builds (`PREFERRED_PROVIDER_virtual/kernel`); there is no per-board kernel
bbappend. Check it with `bitbake -e virtual/kernel | grep '^SRC_URI='`.
If your kernel already has TUN, or you configure it yourself, turn it off in
`conf/local.conf`:

```
NETBIRD_KERNEL_FRAGMENT = ""
```

### Then, either way

`conf/local.conf`:

```
IMAGE_INSTALL:append = " netbird"
```

Settings and setup key go in `recipes-connectivity/netbird/files/autostart.env`,
which is installed as-is to `/etc/netbird/autostart.env`:

```sh
cp files/autostart.env.example files/autostart.env   # then edit it
```

```sh
NB_SETUP_KEY=XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX
#NB_MANAGEMENT_URL=https://netbird.example.com:443   # --management-url
#NB_ADMIN_URL=https://netbird.example.com            # --admin-url
#NB_HOSTNAME=device-001                              # --hostname
NB_DISABLE_DNS=true                                  # remove = --enable-dns
#NB_ALLOW_SERVER_SSH=true                            # --ssh
#NB_ENABLE_SSH_ROOT=true                             # --ssh-root
```

`autostart.env` is git-ignored. It must exist, or `do_fetch` fails with
"Unable to find file": after a fresh clone, copy the example first. Editing it
makes bitbake rebuild the recipe.

Then `bitbake <your-image>`.

For a CPU other than aarch64 / armv7 / x86-64, or another NetBird version,
add the release archive's SHA-256 to `netbird_<version>.bb` (from
`netbird_<version>_checksums.txt` on the GitHub release).

## Things to know

- **The setup key is in the build output.** It ends up in the rootfs, sstate
  cache, buildhistory and every image, and all devices flashed from one image
  share it. Use a key with limited uses and an expiry. If `autostart.env`
  has no `NB_SETUP_KEY` the build only warns: provision the key per device
  later: either add it to `/etc/netbird/autostart.env` and
  `systemctl restart netbird`, or ship `NB_SETUP_KEY_FILE=/etc/netbird/setup-key`
  and drop the key into that file, which `netbird-autostart` picks up on its
  next retry without a restart.
- **Keep the peer identity across updates.** NetBird stores its identity in
  `/etc/netbird` (`config.json`) and state in `/var/lib/netbird`. After an
  A/B image update or on a read-only rootfs these come back empty, and the
  device enrols again as a new peer, using up one setup-key use. Put both on a
  persistent data partition (bind mounts) if the image is updated as a whole.
- **Kernel.** `/dev/net/tun` is required. The fragment is only applied by
  kernel recipes that merge `.cfg` files from `SRC_URI` (linux-yocto and most
  BSP kernels); a kernel built from a plain `defconfig` ignores it, so add
  `CONFIG_TUN=y` there yourself. Kernels older than 5.6 have no in-kernel
  WireGuard; NetBird then uses its userspace WireGuard, which works but uses
  more CPU.
- **Prebuilt binary.** The recipe downloads the official GitHub release
  (checked by SHA-256) instead of compiling Go, so `do_fetch` needs network
  access or a pre-filled `DL_DIR` / mirror.

## Check on the device

```sh
netbird status
systemctl status netbird
journalctl -u netbird -f
```

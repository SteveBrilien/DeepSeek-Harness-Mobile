package com.stevebrilien.dshmobile.core.runtimeandroid

internal object RuntimePins {
    const val RUNTIME_MANIFEST_VERSION = 1

    const val ALPINE_VERSION = "3.24.1"
    const val ALPINE_ROOTFS_URL =
        "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/alpine-minirootfs-3.24.1-aarch64.tar.gz"
    const val ALPINE_ROOTFS_SHA256 =
        "f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259"

    const val PROOT_VERSION = "5.1.107.92"
    const val PROOT_PACKAGE_SHA256 =
        "1f1c983509701f6826f568482c70673ee453a9ba38c9f5fa445a472d6b7524e9"
    const val LIBTALLOC_VERSION = "2.4.3"
    const val LIBTALLOC_PACKAGE_SHA256 =
        "ac81ad623d74c209718b9f3acb2dd702cc8a88c431e820d212229910b4db29da"
    const val LIBANDROID_SHMEM_VERSION = "0.7"
    const val LIBANDROID_SHMEM_PACKAGE_SHA256 =
        "0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6"

    const val PNPM_VERSION = "12.3.4"
    const val DSH_VERSION = "0.1.2-rc.1"
    const val DSH_HTTP_PORT = 3080

    val nativeAssets = mapOf(
        "proot" to "ea47e17da8e6ff4882c169c6508861e5b4be9227e477c6020f4f14facc85c10d",
        "loader" to "44ef39c1e1a18c09f6e4c4b5d6f8bba82d30596598bd155ec162d05c5122ff04",
        "libandroid-shmem.so" to "84475798e07c8174dbbfaec70a827fdb02f19ffa69a589380c13e7507fd0e731",
        "libtalloc.so.2" to "3c9b207c0a6ea2896b7523e03f55d9ab0d9e88baa115d4c32b84058ff4246fbb",
    )
}

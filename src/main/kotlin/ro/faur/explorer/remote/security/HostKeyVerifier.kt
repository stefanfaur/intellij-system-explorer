package ro.faur.explorer.remote.security

import org.apache.sshd.client.config.hosts.KnownHostEntry
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier
import org.apache.sshd.client.keyverifier.ServerKeyVerifier
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.util.net.SshdSocketAddress
import com.intellij.openapi.diagnostic.Logger
import java.net.SocketAddress
import java.nio.file.Path
import java.security.PublicKey

/**
 * Host key verifier implementing Trust On First Use (TOFU) semantics backed by a
 * known_hosts file.
 *
 * Behaviour:
 *  - Known host with matching key  → accept silently
 *  - Known host with changed key   → always reject (throws [SecurityException])
 *  - Unknown host:
 *      - [autoAcceptUnknown] = true            → accept and persist to known_hosts
 *      - [tofuCallback] provided               → call it with (host, fingerprint);
 *                                                 true = accept+persist, false = reject (throws)
 *      - neither                               → reject (throws [SecurityException])
 *
 * @param knownHostsPath   Path to the known_hosts file (created on first write if absent).
 * @param autoAcceptUnknown When true, silently trust and persist any new host key.
 * @param tofuCallback     Optional callback invoked for unknown hosts.  Receives the host
 *                         string and the SHA-256 fingerprint of the presented key.  Return
 *                         true to trust the key, false to reject it.
 */
class HostKeyVerifier(
    private val knownHostsPath: Path,
    private val autoAcceptUnknown: Boolean = false,
    private val tofuCallback: ((host: String, fingerprint: String) -> Boolean)? = null,
) {
    private val LOG = Logger.getInstance(HostKeyVerifier::class.java)

    /**
     * Returns a MINA SSHD [ServerKeyVerifier] that enforces the TOFU policy defined by
     * this [HostKeyVerifier] instance.
     *
     * The returned verifier wraps [KnownHostsServerKeyVerifier] so that successful
     * first-contact decisions are automatically written to [knownHostsPath].
     */
    fun asServerKeyVerifier(): ServerKeyVerifier {
        val delegate = TofuDelegate()
        val knownHosts = object : KnownHostsServerKeyVerifier(delegate, knownHostsPath) {
            override fun acceptModifiedServerKey(
                clientSession: ClientSession,
                remoteAddress: SocketAddress,
                entry: KnownHostEntry,
                expected: PublicKey,
                actual: PublicKey,
            ): Boolean {
                val host = hostString(remoteAddress)
                throw SecurityException(
                    "REMOTE HOST IDENTIFICATION HAS CHANGED for $host! " +
                        "Expected key fingerprint: ${KeyUtils.getFingerPrint(expected)}, " +
                        "got: ${KeyUtils.getFingerPrint(actual)}. " +
                        "Connection refused to prevent a potential man-in-the-middle attack."
                )
            }
        }
        return knownHosts
    }

    // ---------------------------------------------------------------------------
    // Internal delegate — handles the unknown-host TOFU decision
    // ---------------------------------------------------------------------------

    private inner class TofuDelegate : ServerKeyVerifier {
        override fun verifyServerKey(
            clientSession: ClientSession,
            remoteAddress: SocketAddress,
            serverKey: PublicKey,
        ): Boolean {
            val host = hostString(remoteAddress)
            val fingerprint = KeyUtils.getFingerPrint(serverKey)

            if (autoAcceptUnknown) {
                LOG.warn("AUTO-ACCEPT: Trusting unknown host key for $host without verification")
                return true
            }

            if (tofuCallback != null) {
                val accepted = tofuCallback.invoke(host, fingerprint)
                if (!accepted) {
                    throw SecurityException(
                        "Host key rejected by TOFU policy for $host " +
                            "(fingerprint: $fingerprint)"
                    )
                }
                return true
            }

            throw SecurityException(
                "Unknown host $host (fingerprint: $fingerprint). " +
                    "Add the host to known_hosts or configure a TOFU callback."
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun hostString(remoteAddress: SocketAddress): String =
        when (remoteAddress) {
            is SshdSocketAddress -> remoteAddress.hostName
            is java.net.InetSocketAddress -> remoteAddress.hostString
            else -> remoteAddress.toString()
        }
}

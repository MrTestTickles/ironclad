package io.github.ironclad.dns

/**
 * Parsed DNS query representation.
 */
data class DnsPacket(
    val id: Int,
    val flags: Int,
    val qdCount: Int,
    val domain: String,
    val qType: Int,
    val qClass: Int,
    val questionBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DnsPacket
        if (id != other.id) return false
        if (flags != other.flags) return false
        if (domain != other.domain) return false
        if (qType != other.qType) return false
        if (qClass != other.qClass) return false
        if (!questionBytes.contentEquals(other.questionBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + flags
        result = 31 * result + domain.hashCode()
        result = 31 * result + qType
        result = 31 * result + qClass
        result = 31 * result + questionBytes.contentHashCode()
        return result
    }
}

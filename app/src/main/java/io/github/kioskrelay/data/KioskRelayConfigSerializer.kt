package io.github.kioskrelay.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import io.github.kioskrelay.proto.KioskRelayConfigProto
import java.io.InputStream
import java.io.OutputStream

object KioskRelayConfigSerializer : Serializer<KioskRelayConfigProto> {
    override val defaultValue: KioskRelayConfigProto
        get() = ConfigProtoMapper.defaultProto

    override suspend fun readFrom(input: InputStream): KioskRelayConfigProto = try {
        KioskRelayConfigProto.parseFrom(input)
    } catch (exception: InvalidProtocolBufferException) {
        throw CorruptionException("Unable to read KioskRelay configuration", exception)
    }

    override suspend fun writeTo(t: KioskRelayConfigProto, output: OutputStream) {
        t.writeTo(output)
    }
}

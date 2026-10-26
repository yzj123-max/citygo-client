package com.example.citygo.skeleton.data.sync

import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class TripCloudProtocolTest {
    private val id = "7aed9392-2b14-47ad-b3e4-d9d8e9344c52"

    @Test fun emptyPlanAndRetryBodyAreSentByteForByte() = runBlocking {
        val api = RecordingApi()
        val write = TripWrite(0,"7aed9392-2b14-47ad-b3e4-d9d8e9344c53","空行程",TripDocument(),false)
        val raw = """{ "baseVersion":0,"mutationId":"${write.mutationId}","title":"空行程","document":{"plan":null,"messages":[],"options":null},"makeCurrent":false }"""
        val cloud = RemoteTripCloud(api)
        cloud.save(id,write,raw)
        assertEquals(raw,api.body)
        cloud.save(id,write,raw)
        assertEquals(raw,api.body)
        assertTrue(api.body.contains("\"plan\":null"))
    }

    @Test fun http409ReturnsTheRemoteVersionInsteadOfSuccess() = runBlocking {
        val api = RecordingApi()
        api.conflict = true
        try {
            RemoteTripCloud(api).save(id,TripWrite(1,"mutation","本地",TripDocument(),false))
            fail("409 must not be treated as a saved document")
        } catch (e: TripConflictException) {
            assertEquals(3,e.remote.version)
            assertEquals(id,e.remote.tripId)
        }
    }

    private inner class RecordingApi : TripCloudApi {
        var body = ""
        var conflict = false
        override suspend fun save(id: String,write: RequestBody): Response<TripEnvelope<CloudTrip>> {
            val buffer=Buffer();write.writeTo(buffer);body=buffer.readUtf8()
            if (conflict) return Response.error(409,
                """{"code":409,"data":{"tripId":"$id","version":3,"title":"云端","document":{"plan":null,"messages":[],"options":null},"updatedAt":1}}"""
                    .toResponseBody("application/json".toMediaType()))
            return Response.success(TripEnvelope(200,null,CloudTrip(id,1,"空行程",TripDocument(),1)))
        }
        override suspend fun list(): TripEnvelope<List<TripSummary>> = error("unused")
        override suspend fun current(): TripEnvelope<CloudTrip> = error("unused")
        override suspend fun get(id: String): TripEnvelope<CloudTrip> = error("unused")
        override suspend fun activate(id: String): TripEnvelope<CloudTrip> = error("unused")
    }
}

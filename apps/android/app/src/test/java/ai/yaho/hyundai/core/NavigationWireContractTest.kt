package ai.yaho.hyundai.core

import ai.pleos.playground.navi.data.RouteInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class NavigationWireContractTest {
    @Test fun officialRequestSerializationPreservesNamedLatitudeLongitudeAndPoiId() {
        val request=RouteInfo(latitude=37.48544722,longitude=127.03636666,poiName="42dot",
            poiId="36khp37hh8gqcm5nqhe5",address="서울시 강남구 남부순환로 2621",poiSubId="0")
        val wire=Json.encodeToJsonElement(RouteInfo.serializer(),request).jsonObject
        assertEquals(37.48544722,wire.getValue("latitude").jsonPrimitive.double,0.000000001)
        assertEquals(127.03636666,wire.getValue("longitude").jsonPrimitive.double,0.000000001)
        assertEquals("36khp37hh8gqcm5nqhe5",wire.getValue("poiId").jsonPrimitive.content)
    }
}

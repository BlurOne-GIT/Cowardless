package code.blurone.cowardless.nms.manager

import code.blurone.cowardless.nms.v1_20_R3.CommonImpl as v1_20_R3
import code.blurone.cowardless.nms.v1_20_R4.CommonImpl as v1_20_R4

fun getCommon(version: String) = when (version) {
    "1.20.3-R0.1-SNAPSHOT", "1.20.4-R0.1-SNAPSHOT" -> ::v1_20_R3
    "1.20.5-R0.1-SNAPSHOT", "1.20.6-R0.1-SNAPSHOT" -> ::v1_20_R4
    else -> throw IllegalStateException("Unsupported minecraft version.")
}
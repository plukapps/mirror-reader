package com.pluk.reader.ui.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Íconos de la barra inferior: Material Symbols Rounded (peso 400, 24 dp), como en el diseño.
 * Contorno para el destino inactivo y relleno (`FILL 1`) para el activo, sin la librería de íconos.
 */
object NavIcons {
    val Home = materialSymbol(
        "Home",
        "M240-200h120v-200q0-17 11.5-28.5T400-440h160q17 0 28.5 11.5T600-400v200h120v-360L480-740 240-560v360Zm-80 0v-360q0-19 8.5-36t23.5-28l240-180q21-16 48-16t48 16l240 180q15 11 23.5 28t8.5 36v360q0 33-23.5 56.5T720-120H560q-17 0-28.5-11.5T520-160v-200h-80v200q0 17-11.5 28.5T400-120H240q-33 0-56.5-23.5T160-200Zm320-270Z",
    )
    val HomeFilled = materialSymbol(
        "HomeFilled",
        "M160-200v-360q0-19 8.5-36t23.5-28l240-180q21-16 48-16t48 16l240 180q15 11 23.5 28t8.5 36v360q0 33-23.5 56.5T720-120H600q-17 0-28.5-11.5T560-160v-200q0-17-11.5-28.5T520-400h-80q-17 0-28.5 11.5T400-360v200q0 17-11.5 28.5T360-120H240q-33 0-56.5-23.5T160-200Z",
    )

    /** La lupa no cambia al rellenarse. */
    val Search = materialSymbol(
        "Search",
        "M380-320q-109 0-184.5-75.5T120-580q0-109 75.5-184.5T380-840q109 0 184.5 75.5T640-580q0 44-14 83t-38 69l224 224q11 11 11 28t-11 28q-11 11-28 11t-28-11L532-372q-30 24-69 38t-83 14Zm0-80q75 0 127.5-52.5T560-580q0-75-52.5-127.5T380-760q-75 0-127.5 52.5T200-580q0 75 52.5 127.5T380-400Z",
    )
    val Shelves = materialSymbol(
        "Shelves",
        "M160-40q-17 0-28.5-11.5T120-80v-800q0-17 11.5-28.5T160-920q17 0 28.5 11.5T200-880v40h560v-40q0-17 11.5-28.5T800-920q17 0 28.5 11.5T840-880v800q0 17-11.5 28.5T800-40q-17 0-28.5-11.5T760-80v-40H200v40q0 17-11.5 28.5T160-40Zm40-480h80v-120q0-17 11.5-28.5T320-680h160q17 0 28.5 11.5T520-640v120h240v-240H200v240Zm0 320h240v-120q0-17 11.5-28.5T480-360h160q17 0 28.5 11.5T680-320v120h80v-240H200v240Zm160-320h80v-80h-80v80Zm160 320h80v-80h-80v80ZM360-520h80-80Zm160 320h80-80Z",
    )
    val ShelvesFilled = materialSymbol(
        "ShelvesFilled",
        "M160-40q-17 0-28.5-11.5T120-80v-800q0-17 11.5-28.5T160-920q17 0 28.5 11.5T200-880v40h560v-40q0-17 11.5-28.5T800-920q17 0 28.5 11.5T840-880v800q0 17-11.5 28.5T800-40q-17 0-28.5-11.5T760-80v-40H200v40q0 17-11.5 28.5T160-40Zm40-480h80v-120q0-17 11.5-28.5T320-680h160q17 0 28.5 11.5T520-640v120h240v-240H200v240Zm0 320h240v-120q0-17 11.5-28.5T480-360h160q17 0 28.5 11.5T680-320v120h80v-240H200v240Z",
    )
    val Person = materialSymbol(
        "Person",
        "M480-480q-66 0-113-47t-47-113q0-66 47-113t113-47q66 0 113 47t47 113q0 66-47 113t-113 47ZM160-240v-32q0-34 17.5-62.5T224-378q62-31 126-46.5T480-440q66 0 130 15.5T736-378q29 15 46.5 43.5T800-272v32q0 33-23.5 56.5T720-160H240q-33 0-56.5-23.5T160-240Zm80 0h480v-32q0-11-5.5-20T700-306q-54-27-109-40.5T480-360q-56 0-111 13.5T260-306q-9 5-14.5 14t-5.5 20v32Zm240-320q33 0 56.5-23.5T560-640q0-33-23.5-56.5T480-720q-33 0-56.5 23.5T400-640q0 33 23.5 56.5T480-560Zm0-80Zm0 400Z",
    )
    val PersonFilled = materialSymbol(
        "PersonFilled",
        "M480-480q-66 0-113-47t-47-113q0-66 47-113t113-47q66 0 113 47t47 113q0 66-47 113t-113 47ZM160-240v-32q0-34 17.5-62.5T224-378q62-31 126-46.5T480-440q66 0 130 15.5T736-378q29 15 46.5 43.5T800-272v32q0 33-23.5 56.5T720-160H240q-33 0-56.5-23.5T160-240Z",
    )
}

/**
 * Ícono de Material Symbols Rounded a partir del trazado del SVG. Material Symbols dibuja en una
 * caja de 960 con `viewBox="0 -960 960 960"`: se baja 960 para que entre.
 */
internal fun materialSymbol(name: String, pathData: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 960f, 960f)
        .addGroup(translationY = 960f)
        .addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = SolidColor(Color.Black),
        )
        .clearGroup()
        .build()

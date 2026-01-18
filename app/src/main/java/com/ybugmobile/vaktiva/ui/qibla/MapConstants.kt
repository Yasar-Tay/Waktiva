package com.ybugmobile.vaktiva.ui.qibla

object MapConstants {
    const val STREET_STYLE = "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"
    const val SATELLITE_STYLE_JSON = """
    {
      "version": 8,
      "sources": {
        "raster-tiles": {
          "type": "raster",
          "tiles": ["https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
          "tileSize": 256,
          "attribution": "Esri"
        }
      },
      "layers": [{"id": "simple-tiles", "type": "raster", "source": "raster-tiles", "minzoom": 0, "maxzoom": 20}]
    }
    """
    const val USER_ARROW_ID = "user-arrow"
    const val CUSTOM_ARROW_ID = "custom-arrow"
    const val KAABA_LAT = 21.4225
    const val KAABA_LNG = 39.8262
    const val DEFAULT_ZOOM = 18.0
}

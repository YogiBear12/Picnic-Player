package app.picnic.player.data.media

fun countLabel(count: Int, singular: String, plural: String = "${singular}s"): String = if (count == 1) "1 $singular" else "$count $plural"

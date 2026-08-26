package app.picnic.player.text

fun countLabel(count: Int, singular: String, plural: String = "${singular}s"): String = if (count == 1) "1 $singular" else "$count $plural"

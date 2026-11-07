package com.basil.grocyscanner.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.basil.grocyscanner.R
import com.basil.grocyscanner.viewmodel.ScanSound

/**
 * Plays the scan sound cues from res/raw.
 *
 *   ERROR         scan_error         Grocy refused it, or not a product code
 *   SERVER_ERROR  scan_server_error  Grocy unreachable / timed out / 5xx
 *   PRINTED       scan_printed       purchase auto-print sent its labels
 *   UNDONE        scan_undone        undo succeeded
 *
 * The sounds are from Kenney's "Interface Sounds" pack (www.kenney.nl), CC0:
 * error_002, error_007, confirmation_002 and minimize_008. To change one, drop a
 * replacement in res/raw under the same name.
 *
 * SoundPool decodes them once up front, so a cue starts with no delay.
 */
class ScanSounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                // Media usage so the cue still plays with the ringer on silent
                // or vibrate. It follows the media volume.
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds: Map<ScanSound, Int> = mapOf(
        ScanSound.ERROR to R.raw.scan_error,
        ScanSound.SERVER_ERROR to R.raw.scan_server_error,
        ScanSound.PRINTED to R.raw.scan_printed,
        ScanSound.UNDONE to R.raw.scan_undone,
    ).mapValues { (_, res) -> pool.load(context, res, 1) }

    fun play(sound: ScanSound) {
        val id = soundIds[sound] ?: return
        // Returns 0 (and plays nothing) if the sound hasn't finished loading,
        // which only matters for a scan in the first moment after launch.
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() = pool.release()
}

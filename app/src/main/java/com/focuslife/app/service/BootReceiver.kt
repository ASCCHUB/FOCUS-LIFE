package com.focuslife.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focuslife.app.worker.UsageWorker

class BootReceiver : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) { UsageWorker.schedule(context) } }

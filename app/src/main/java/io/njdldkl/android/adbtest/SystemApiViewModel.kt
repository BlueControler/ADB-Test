package io.njdldkl.android.adbtest

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.njdldkl.android.adbtest.system.SystemApi
import io.njdldkl.android.adbtest.system.SystemProtocolHandler
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class SystemApiViewModel : ViewModel() {
    var serverUrl by mutableStateOf("ws://localhost:8080")
    var calendarGranted by mutableStateOf(false)
    var locationGranted by mutableStateOf(false)
    var notificationGranted by mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
    var serviceRunning by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var resultJson by mutableStateOf("")
    private var nextRequestId = 1
    private var lastEventId: Long? = null

    fun refresh(context: Context) {
        calendarGranted =
            context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        locationGranted =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        notificationGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
    }

    fun callListApps(context: Context, type: String) {
        call(
            context = context,
            message = "listApps",
            data = JSONObject().put("type", type)
        )
    }

    fun callCreateEvent(context: Context) {
        val now = System.currentTimeMillis()
        call(
            context = context,
            message = "createEvent",
            data = JSONObject().put(
                "event",
                JSONObject()
                    .put("title", "ADBTest 本地测试")
                    .put("description", "System API local test")
                    .put("eventLocation", "Local")
                    .put("dtstart", now + 60 * 60 * 1000)
                    .put("dtend", now + 2 * 60 * 60 * 1000)
                    .put("allDay", false)
                    .put("eventTimezone", TimeZone.getDefault().id)
                    .put("availability", "busy")
                    .put("status", "confirmed")
            ),
            onResponse = { response ->
                val data = response.optJSONObject("data")
                if (data != null && data.has("id")) {
                    lastEventId = data.getLong("id")
                }
            }
        )
    }

    fun callListEvents(context: Context) {
        val now = System.currentTimeMillis()
        call(
            context = context,
            message = "listEvents",
            data = JSONObject()
                .put("start", now - 24 * 60 * 60 * 1000)
                .put("end", now + 7 * 24 * 60 * 60 * 1000)
        )
    }

    fun callUpdateEvent(context: Context) {
        val id = lastEventId
        if (id == null) {
            error = "请先创建日程。"
            return
        }
        call(
            context = context,
            message = "updateEvent",
            data = JSONObject().put(
                "event",
                JSONObject()
                    .put("_id", id)
                    .put("title", "ADBTest 本地测试 - 已更新")
                    .put("status", "confirmed")
            )
        )
    }

    fun callListReminders(context: Context) {
        val id = lastEventId
        if (id == null) {
            error = "请先创建日程。"
            return
        }
        call(
            context = context,
            message = "listReminders",
            data = JSONObject().put("eventId", id)
        )
    }

    fun callUpdateReminders(context: Context) {
        val id = lastEventId
        if (id == null) {
            error = "请先创建日程。"
            return
        }
        call(
            context = context,
            message = "updateReminders",
            data = JSONObject()
                .put("eventId", id)
                .put(
                    "reminders",
                    JSONArray().put(
                        JSONObject()
                            .put("minutes", 10)
                            .put("method", "alert")
                    )
                )
        )
    }

    fun callGetLocation(context: Context) {
        call(context = context, message = "getLocation", data = null)
    }

    fun clearResult() {
        error = null
        resultJson = ""
    }

    private fun call(
        context: Context,
        message: String,
        data: Any?,
        onResponse: (JSONObject) -> Unit = {}
    ) {
        error = null
        resultJson = "请求中..."
        val requestId = nextRequestId++
        viewModelScope.launch {
            val handler = SystemProtocolHandler(SystemApi(context.applicationContext))
            val request = SystemProtocolHandler.request(message, requestId, data)
            val response = handler.handleRequest(request)
            onResponse(response)
            resultJson = response.toString(2)
            refresh(context)
        }
    }
}

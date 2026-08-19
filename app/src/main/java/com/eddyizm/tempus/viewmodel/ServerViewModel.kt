package com.eddyizm.tempus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.eddyizm.tempus.model.Server
import com.eddyizm.tempus.repository.ServerRepository

class ServerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ServerRepository()
    val allServers: LiveData<List<Server>> = repository.liveServer

    fun insertServer(server: Server) {
        repository.insert(server)
    }

    fun updateServer(server: Server) {
        repository.update(server)
    }

    fun deleteServer(server: Server) {
        repository.delete(server)
    }
}
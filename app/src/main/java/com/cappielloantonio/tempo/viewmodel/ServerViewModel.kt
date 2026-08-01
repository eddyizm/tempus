package com.cappielloantonio.tempo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.cappielloantonio.tempo.model.Server
import com.cappielloantonio.tempo.repository.ServerRepository

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
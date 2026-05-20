package com.offbuss.model

data class Route(
    val linha: String,
    val pontoInicial: String,
    val pontoFinal: String,
    val temSentido2: Boolean = false
)

data class Horarios(
    val linha: String,
    val sentido: Int,
    val pontoPartida: String,
    val horarios: List<String>
)
package com.vrsoftware.pedidos.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pedido {
    private String id;
    private String produto;
    private int quantidade;

    public Pedido(String produto, int quantidade) {
        this.produto = produto;
        this.quantidade = quantidade;
    }
}

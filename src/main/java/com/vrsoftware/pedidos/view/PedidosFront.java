package com.vrsoftware.pedidos.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vrsoftware.pedidos.model.Pedido;
import okhttp3.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PedidosFront extends JFrame {

    private final JTextField produtoField = new JTextField(15);
    private final JTextField quantidadeField = new JTextField(5);
    private final JButton enviarButton = new JButton("Enviar Pedido");
    private final DefaultTableModel tableModel = new DefaultTableModel(new String[]{"ID", "Status"}, 0);
    private final JTable pedidosTable = new JTable(tableModel);

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, String> pedidosPendentes = new ConcurrentHashMap<>();

    private final String baseUrl = "http://localhost:8080/api/pedidos";

    public PedidosFront() {
        super("Sistema de Pedidos");

        setLayout(new BorderLayout());

        JPanel inputPanel = new JPanel();
        inputPanel.add(new JLabel("Produto:"));
        inputPanel.add(produtoField);
        inputPanel.add(new JLabel("Quantidade:"));
        inputPanel.add(quantidadeField);
        inputPanel.add(enviarButton);

        add(inputPanel, BorderLayout.NORTH);
        add(new JScrollPane(pedidosTable), BorderLayout.CENTER);

        enviarButton.addActionListener(e -> enviarPedido());

        // Timer para polling a cada 4 segundos
        Timer timer = new Timer(4000, e -> atualizarStatusPedidos());
        timer.start();

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void enviarPedido() {
        String produto = produtoField.getText().trim();
        String qtdStr = quantidadeField.getText().trim();

        if (produto.isEmpty() || qtdStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Preencha produto e quantidade!", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int quantidade;
        try {
            quantidade = Integer.parseInt(qtdStr);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Quantidade deve ser número inteiro!", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Pedido pedido = new Pedido(produto, quantidade);

        try {
            String jsonPedido = mapper.writeValueAsString(pedido);

            RequestBody body = RequestBody.create(jsonPedido, MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(baseUrl)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(PedidosFront.this,
                            "Erro ao enviar pedido: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(PedidosFront.this,
                                "Falha no envio do pedido: HTTP " + response.code(),
                                "Erro", JOptionPane.ERROR_MESSAGE));
                        return;
                    }

                    SwingUtilities.invokeLater(() -> {
                        pedidosPendentes.put(pedido.getId(), "ENVIADO, AGUARDANDO PROCESSO");
                        tableModel.addRow(new Object[]{pedido.getId(), "ENVIADO, AGUARDANDO PROCESSO"});
                    });
                }
            });

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro interno: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void atualizarStatusPedidos() {
        pedidosPendentes.forEach((id, status) -> {
            if (!status.equals("ENVIADO, AGUARDANDO PROCESSO")) return;

            String url = baseUrl + "/status/" + id;
            Request request = new Request.Builder().url(url).get().build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException ex) {
                    // Opcional: log ou ignore
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) return;

                    String json = response.body().string();
                    Map<String, String> mapaStatus = mapper.readValue(json, Map.class);
                    String novoStatus = mapaStatus.get("status");

                    if ("SUCESSO".equalsIgnoreCase(novoStatus) || "FALHA".equalsIgnoreCase(novoStatus)) {
                        pedidosPendentes.put(id, novoStatus);

                        SwingUtilities.invokeLater(() -> {
                            for (int i = 0; i < tableModel.getRowCount(); i++) {
                                if (tableModel.getValueAt(i, 0).equals(id)) {
                                    tableModel.setValueAt(novoStatus, i, 1);
                                    break;
                                }
                            }
                        });
                    }
                }
            });
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(PedidosFront::new);
    }
}
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ClientGUI {
    private JFrame frame;
    private JTabbedPane tabbedPane;
    private LogPanel logPanel;
    private JTextArea serverInfoArea;
    private JTextField messageField;
    private JTextField fileSelectedField;
    private JButton connectButton;
    private JButton disconnectButton;
    private JButton selectFileButton;
    private JButton sendButton;
    
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private volatile boolean isConnected = false;
    private Path selectedFilePath;
    private Set<String> allowedExtensions = new HashSet<>();

    public ClientGUI() {
        initializeGUI();
    }

    private void initializeGUI() {
        frame = new JFrame("Cliente de Transferência");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setPreferredSize(new Dimension(650, 500));

        // Aba Principal
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Painel Norte - Conexão
        JPanel connectionPanel = new JPanel(new GridLayout(1, 3, 5, 5));
        connectButton = new JButton("Conectar");
        connectButton.setBackground(new Color(50, 50, 150));
        connectButton.setForeground(Color.WHITE);
        connectButton.setFont(new Font("Arial", Font.BOLD, 14));
        disconnectButton = new JButton("Desconectar");
        disconnectButton.setEnabled(false);
        disconnectButton.setBackground(new Color(150, 50, 50));
        disconnectButton.setForeground(Color.WHITE);
        
        connectionPanel.add(connectButton);
        connectionPanel.add(new JLabel("Server: localhost:" + FileTransferProtocol.PORT));
        connectionPanel.add(disconnectButton);

        mainPanel.add(connectionPanel, BorderLayout.NORTH);

        // Painel Central - Info Servidor / Chat
        serverInfoArea = new JTextArea("Status: Desconectado. Conecte-se para ver as configurações do Servidor.");
        serverInfoArea.setEditable(false);
        serverInfoArea.setLineWrap(true);
        JScrollPane serverInfoScroll = new JScrollPane(serverInfoArea);
        mainPanel.add(serverInfoScroll, BorderLayout.CENTER);
        
        // Painel Sul - Envio
        JPanel sendPanel = new JPanel(new BorderLayout(5, 5));
        
        // Linha 1: Arquivo
        JPanel filePanel = new JPanel(new BorderLayout(5, 5));
        selectFileButton = new JButton("Selecionar Arquivo");
        fileSelectedField = new JTextField("Nenhum arquivo selecionado.");
        fileSelectedField.setEditable(false);
        filePanel.add(selectFileButton, BorderLayout.WEST);
        filePanel.add(fileSelectedField, BorderLayout.CENTER);
        
        // Linha 2: Mensagem e Enviar
        JPanel msgPanel = new JPanel(new BorderLayout(5, 5));
        messageField = new JTextField("Digite sua mensagem aqui...");
        sendButton = new JButton("Enviar");
        sendButton.setEnabled(false);
        
        msgPanel.add(messageField, BorderLayout.CENTER);
        msgPanel.add(sendButton, BorderLayout.EAST);
        
        sendPanel.add(filePanel, BorderLayout.NORTH);
        sendPanel.add(msgPanel, BorderLayout.SOUTH);
        
        mainPanel.add(sendPanel, BorderLayout.SOUTH);
        
        // Configuração da aba
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Principal", mainPanel);

        // Aba de Log
        logPanel = new LogPanel();
        tabbedPane.addTab("Log", logPanel);

        frame.add(tabbedPane);
        frame.pack();
        frame.setLocationRelativeTo(null);

        // Listeners
        connectButton.addActionListener(e -> connectToServer());
        disconnectButton.addActionListener(e -> disconnectFromServer());
        sendButton.addActionListener(e -> sendMessageOrFile());
        selectFileButton.addActionListener(e -> selectFile());
        
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnectFromServer();
            }
        });
    }
    
    // Lógica de Conexão e Comunicação

    private void connectToServer() {
        new Thread(() -> {
            try {
                logPanel.log("Tentando conectar ao servidor em localhost:" + FileTransferProtocol.PORT + "...");
                socket = new Socket("localhost", FileTransferProtocol.PORT);
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
                isConnected = true;
                
                SwingUtilities.invokeLater(() -> {
                    connectButton.setEnabled(false);
                    disconnectButton.setEnabled(true);
                    sendButton.setEnabled(true);
                    serverInfoArea.setText("Conectado! Aguardando lista de arquivos permitidos...");
                });
                logPanel.log("Conectado com sucesso!");

                // Iniciar thread de escuta para receber configurações e logs do servidor
                listenForServerMessages();

            } catch (IOException e) {
                logPanel.log("Falha ao conectar: " + e.getMessage());
                JOptionPane.showMessageDialog(frame, "Falha ao conectar ao servidor.", "Erro de Conexão", JOptionPane.ERROR_MESSAGE);
                isConnected = false;
                SwingUtilities.invokeLater(() -> {
                    connectButton.setEnabled(true);
                    disconnectButton.setEnabled(false);
                    sendButton.setEnabled(false);
                });
            }
        }).start();
    }

    private void disconnectFromServer() {
        if (!isConnected) return;
        isConnected = false;
        try {
            if (out != null) out.writeObject(FileTransferProtocol.MSG_SERVER_SHUTDOWN);
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
            logPanel.log("Desconectado do servidor.");
        } catch (IOException e) {
            logPanel.log("Erro ao fechar a conexão: " + e.getMessage());
        } finally {
            SwingUtilities.invokeLater(() -> {
                connectButton.setEnabled(true);
                disconnectButton.setEnabled(false);
                sendButton.setEnabled(false);
                fileSelectedField.setText("Nenhum arquivo selecionado.");
                selectedFilePath = null;
                allowedExtensions.clear();
                updateServerInfo("Desconectado.");
            });
        }
    }

    private void listenForServerMessages() {
        new Thread(() -> {
            try {
                while (isConnected) {
                    Object receivedObject = in.readObject();
                    if (receivedObject instanceof String) {
                        String message = (String) receivedObject;
                        
                        if (message.startsWith(FileTransferProtocol.MSG_FILE_TYPE_LIST)) {
                            String extensionsStr = message.substring(FileTransferProtocol.MSG_FILE_TYPE_LIST.length());
                            allowedExtensions = new HashSet<>(Arrays.asList(extensionsStr.split(",")));
                            updateServerInfo("Conectado.\nTipos de arquivo permitidos: " + allowedExtensions.toString());
                            logPanel.log("Lista de tipos de arquivo recebida: " + allowedExtensions.toString());
                        } else if (message.equals(FileTransferProtocol.MSG_SERVER_READY)) {
                             logPanel.log("Servidor pronto para receber dados.");
                        } else if (message.startsWith(FileTransferProtocol.MSG_FILE_TYPE_ERROR)) {
                            String errorExt = message.substring(FileTransferProtocol.MSG_FILE_TYPE_ERROR.length());
                            logPanel.log("Erro do Servidor: Tipo de arquivo ."+errorExt+" não permitido!");
                            JOptionPane.showMessageDialog(frame, "Servidor rejeitou: Tipo de arquivo ."+errorExt+" não permitido!", "Erro de Envio", JOptionPane.WARNING_MESSAGE);
                        } else {
                            logPanel.log("Mensagem do servidor: " + message);
                        }
                    }
                }
            } catch (EOFException e) {
                logPanel.log("O servidor fechou a conexão.");
            } catch (IOException | ClassNotFoundException e) {
                 if (isConnected) logPanel.log("Erro de comunicação: " + e.getMessage());
            } finally {
                disconnectFromServer();
            }
        }).start();
    }
    
    private void updateServerInfo(String info) {
        SwingUtilities.invokeLater(() -> serverInfoArea.setText("Status: " + info));
    }

    // Lógica de Interface Gráfica

    private void selectFile() {
        if (!isConnected) {
            JOptionPane.showMessageDialog(frame, "Conecte-se ao servidor primeiro.", "Erro", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Selecione o Arquivo para Enviar");

        // Configurar o filtro de extensões
        if (!allowedExtensions.isEmpty()) {
             String description = "Arquivos Permitidos (" + String.join(", ", allowedExtensions.stream().map(e -> "*." + e).collect(Collectors.toList())) + ")";
             String[] extensionsArray = allowedExtensions.toArray(new String[0]);
             FileNameExtensionFilter filter = new FileNameExtensionFilter(description, extensionsArray);
             fileChooser.setFileFilter(filter);
        } else {
            // Se ainda não recebeu a lista (ou a lista está vazia), usa um filtro padrão.
            fileChooser.setFileFilter(new FileNameExtensionFilter("Todos os Arquivos (*.*)", "*"));
        }

        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFilePath = fileChooser.getSelectedFile().toPath();
            fileSelectedField.setText(selectedFilePath.getFileName().toString());
            logPanel.log("Arquivo selecionado: " + selectedFilePath.getFileName());
        }
    }

    private void sendMessageOrFile() {
        if (!isConnected) {
            JOptionPane.showMessageDialog(frame, "Conecte-se ao servidor primeiro.", "Erro", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        new Thread(() -> {
            try {
                if (selectedFilePath != null) {
                    // Enviar Arquivo
                    File file = selectedFilePath.toFile();
                    String fileName = file.getName();
                    byte[] fileData = Files.readAllBytes(selectedFilePath);
                    
                    // Validação final de extensão (A GUI deveria ter filtrado, mas é bom validar)
                    String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
                    if (!allowedExtensions.contains(extension)) {
                         logPanel.log("ERRO LOCAL: Extensão ."+extension+" não permitida pelo servidor.");
                         JOptionPane.showMessageDialog(frame, "O tipo de arquivo ."+extension+" não está na lista de tipos permitidos pelo Servidor: "+allowedExtensions.toString(), "Erro de Validação", JOptionPane.ERROR_MESSAGE);
                         return;
                    }
                    
                    FileTransferProtocol.TransferData data = new FileTransferProtocol.TransferData(fileName, fileData);
                    out.writeObject(data);
                    out.flush();
                    logPanel.log("Arquivo '" + fileName + "' (" + fileData.length + " bytes) enviado.");
                    
                    // Limpar seleção
                    selectedFilePath = null;
                    SwingUtilities.invokeLater(() -> fileSelectedField.setText("Nenhum arquivo selecionado."));
                    
                } else if (!messageField.getText().trim().isEmpty()) {
                    // Enviar Mensagem de Texto
                    String message = messageField.getText();
                    FileTransferProtocol.TransferData data = new FileTransferProtocol.TransferData(message);
                    out.writeObject(data);
                    out.flush();
                    logPanel.log("Mensagem de texto enviada: " + message);
                    SwingUtilities.invokeLater(() -> messageField.setText("")); // Limpar campo
                } else {
                    JOptionPane.showMessageDialog(frame, "Selecione um arquivo OU digite uma mensagem.", "Atenção", JOptionPane.WARNING_MESSAGE);
                }
                
            } catch (IOException e) {
                logPanel.log("Erro ao enviar dados: " + e.getMessage());
                JOptionPane.showMessageDialog(frame, "Erro ao enviar dados: " + e.getMessage(), "Erro de Comunicação", JOptionPane.ERROR_MESSAGE);
                disconnectFromServer();
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientGUI().frame.setVisible(true));
    }
}
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ServerGUI.java
 * Classe principal do Servidor, corrigida para usar uma referência direta ao JLabel de Status.
 * Depende de FileTransferProtocol.java e LogPanel.java.
 */
public class ServerGUI {
    // --- Componentes da GUI e Estado do Servidor ---
    private JFrame frame;
    private JTabbedPane tabbedPane;
    private LogPanel logPanel;
    private JTextArea clientInfoArea;
    private JTextField allowedExtensionsField;
    private JButton startStopButton;
    private JLabel statusLabel; // NOVO: Referência direta para o JLabel de Status
    private ServerSocket serverSocket;
    private Set<String> allowedExtensions;
    private volatile boolean isRunning = false;
    private ClientHandler clientHandler; // Suporta apenas uma conexão ativa por vez

    public ServerGUI() {
        allowedExtensions = new HashSet<>(Arrays.asList("txt", "pdf", "jpg", "png")); // Padrão
        initializeGUI();
    }

    private void initializeGUI() {
        frame = new JFrame("Servidor de Transferência - " + FileTransferProtocol.PORT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setPreferredSize(new Dimension(650, 400));

        // Aba Principal
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Painel de Configuração
        JPanel configPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        
        configPanel.add(new JLabel("Extensões Permitidas (separadas por vírgula):"));
        allowedExtensionsField = new JTextField(allowedExtensions.stream().collect(Collectors.joining(",")));
        configPanel.add(allowedExtensionsField);
        
        startStopButton = new JButton("Iniciar Servidor");
        startStopButton.setBackground(new Color(50, 150, 50));
        startStopButton.setForeground(Color.WHITE);
        startStopButton.setFont(new Font("Arial", Font.BOLD, 14));
        configPanel.add(startStopButton);
        
        // CORREÇÃO: Inicializa e armazena a referência do JLabel
        statusLabel = new JLabel("Status: Offline"); 
        configPanel.add(statusLabel); 

        mainPanel.add(configPanel, BorderLayout.NORTH);

        // Área de informações do cliente
        clientInfoArea = new JTextArea("Aguardando Conexão...");
        clientInfoArea.setEditable(false);
        clientInfoArea.setLineWrap(true);
        JScrollPane clientInfoScroll = new JScrollPane(clientInfoArea);
        mainPanel.add(clientInfoScroll, BorderLayout.CENTER);

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
        startStopButton.addActionListener(e -> toggleServer());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopServer();
            }
        });
    }

    private void toggleServer() {
        if (isRunning) {
            stopServer();
        } else {
            startServer();
        }
    }

    private void startServer() {
        // Configurar extensões permitidas
        String extensionsStr = allowedExtensionsField.getText().toLowerCase().replaceAll("\\s+", "");
        allowedExtensions = new HashSet<>(Arrays.asList(extensionsStr.split(",")));
        allowedExtensions.remove(""); // Remove entradas vazias

        // Iniciar Servidor em nova Thread
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(FileTransferProtocol.PORT);
                isRunning = true;
                logPanel.log("Servidor iniciado na porta " + FileTransferProtocol.PORT);
                
                SwingUtilities.invokeLater(() -> {
                    startStopButton.setText("Parar Servidor");
                    startStopButton.setBackground(new Color(150, 50, 50));
                    allowedExtensionsField.setEditable(false);
                    // CORREÇÃO: Usa a referência direta
                    statusLabel.setText("Status: Online");
                });

                while (isRunning) {
                    logPanel.log("Aguardando conexões...");
                    Socket clientSocket = serverSocket.accept();
                    logPanel.log("Cliente conectado: " + clientSocket.getInetAddress());

                    if (clientHandler != null && clientHandler.isRunning()) {
                        logPanel.log("Cliente rejeitado: Servidor ocupado com outra conexão.");
                        // Enviar mensagem de 'Servidor Ocupado' e fechar
                        new PrintWriter(clientSocket.getOutputStream(), true).println("Server Busy");
                        clientSocket.close();
                        continue;
                    }

                    clientHandler = new ClientHandler(clientSocket, allowedExtensions);
                    new Thread(clientHandler).start();
                }
            } catch (IOException e) {
                if (isRunning) {
                    logPanel.log("Erro no servidor: " + e.getMessage());
                }
            } finally {
                isRunning = false;
                SwingUtilities.invokeLater(() -> {
                    startStopButton.setText("Iniciar Servidor");
                    startStopButton.setBackground(new Color(50, 150, 50));
                    allowedExtensionsField.setEditable(true);
                    // CORREÇÃO: Usa a referência direta
                    statusLabel.setText("Status: Offline");
                });
            }
        }).start();
    }

    private void stopServer() {
        if (!isRunning) return;
        isRunning = false;
        try {
            if (clientHandler != null) clientHandler.closeConnection();
            if (serverSocket != null) serverSocket.close();
            logPanel.log("Servidor parado.");
        } catch (IOException e) {
            logPanel.log("Erro ao parar o servidor: " + e.getMessage());
        }
        clientHandler = null;
    }

    // Handler de cliente (modularização interna)
    private class ClientHandler implements Runnable {
        private Socket clientSocket;
        private Set<String> allowed;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private boolean connected = true;

        public ClientHandler(Socket socket, Set<String> allowedExtensions) {
            this.clientSocket = socket;
            this.allowed = allowedExtensions;
        }

        public boolean isRunning() { return connected; }

        @Override
        public void run() {
            try {
                // Configurar Streams. A ordem é crucial.
                out = new ObjectOutputStream(clientSocket.getOutputStream());
                in = new ObjectInputStream(clientSocket.getInputStream());
                
                // Enviar tipos de arquivo permitidos
                String allowedMsg = FileTransferProtocol.MSG_FILE_TYPE_LIST + String.join(",", allowed);
                out.writeObject(allowedMsg);
                out.flush();
                logPanel.log("Enviado para o Cliente a lista de tipos permitidos: " + allowed);
                
                // Informar que está pronto
                out.writeObject(FileTransferProtocol.MSG_SERVER_READY);
                out.flush();
                
                SwingUtilities.invokeLater(() -> clientInfoArea.setText("Cliente Conectado.\nTipos Permitidos: " + allowed.toString()));

                // Loop de Recebimento
                while (connected) {
                    Object receivedObject = in.readObject();

                    if (receivedObject instanceof FileTransferProtocol.TransferData) {
                        handleDataTransfer((FileTransferProtocol.TransferData) receivedObject);
                    } else if (receivedObject instanceof String) {
                        String message = (String) receivedObject;
                        if (message.equals(FileTransferProtocol.MSG_SERVER_SHUTDOWN)) {
                            logPanel.log("Cliente solicitou desconexão.");
                            break;
                        } else {
                            logPanel.log("Mensagem bruta recebida: " + message);
                        }
                    }
                }
            } catch (EOFException e) {
                logPanel.log("Cliente se desconectou normalmente.");
            } catch (IOException | ClassNotFoundException e) {
                if (connected) logPanel.log("Erro de comunicação com o cliente: " + e.getMessage());
            } finally {
                closeConnection();
            }
        }

        private void handleDataTransfer(FileTransferProtocol.TransferData data) throws IOException {
            logPanel.log("Recebida requisição de: " + data);
            
            if (data.getType().equals("TEXT")) {
                logPanel.log("MENSAGEM DE TEXTO: " + data.getTextMessage());
                clientInfoArea.append("\n[MSG] " + data.getTextMessage());
            } else if (data.getType().equals("FILE")) {
                String extension = data.getExtension();
                
                // Validação de Extensão
                if (!allowed.contains(extension)) {
                    logPanel.log("ERRO: Tipo de arquivo ."+extension+" não permitido!");
                    // Enviar notificação de erro ao cliente
                    out.writeObject(FileTransferProtocol.MSG_FILE_TYPE_ERROR + extension);
                    out.flush();
                    return;
                }
                
                // Salvar Arquivo
                Path receivedDir = Paths.get("received_files");
                if (!Files.exists(receivedDir)) {
                    Files.createDirectory(receivedDir);
                }
                Path filePath = receivedDir.resolve(data.getFileName());
                Files.write(filePath, data.getFileData());
                logPanel.log("Arquivo Salvo: " + filePath.toAbsolutePath());
                clientInfoArea.append("\n[FILE] Arquivo Recebido e Salvo: " + data.getFileName());
            }
        }

        public void closeConnection() {
            if (!connected) return;
            connected = false;
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (clientSocket != null) clientSocket.close();
                logPanel.log("Conexão com o cliente fechada.");
                SwingUtilities.invokeLater(() -> clientInfoArea.setText("Aguardando Conexão..."));
            } catch (IOException e) {
                logPanel.log("Erro ao fechar o socket do cliente: " + e.getMessage());
            }
            clientHandler = null; // Libera o handler para aceitar novas conexões
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ServerGUI().frame.setVisible(true));
    }
}
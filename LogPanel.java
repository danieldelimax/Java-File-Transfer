import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogPanel extends JPanel {
    private JTextArea logArea;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    public LogPanel() {
        setLayout(new BorderLayout());
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        
        add(scrollPane, BorderLayout.CENTER);
    }

    // Método para adicionar uma mensagem formatada ao log
    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = dateFormat.format(new Date());
            logArea.append("[" + timestamp + "] " + message + "\n");
            // Scroll automático para o final
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
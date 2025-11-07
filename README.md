# 🚀 java-file-transfer
## Transferência de Arquivos Cliente-Servidor com Controle de Tipos (Java Swing)

Este projeto demonstra uma aplicação **cliente-servidor robusta** em Java, utilizando a biblioteca **Swing** para criar interfaces gráficas funcionais. O principal diferencial é a implementação de um protocolo onde o servidor define ativamente os tipos de arquivos que pode receber (`.txt`, `.pdf`, `.jpg`, etc.), e o cliente é informado dessa restrição ao se conectar.

---

## ✨ Funcionalidades

### Servidor (`ServerGUI.java`)
* **Configuração Dinâmica**: Permite que o administrador defina as **extensões de arquivo permitidas** (ex: `txt`, `jpg`, `pdf`) antes de iniciar o servidor.
* **Controle de Acesso**: Informa o cliente sobre os tipos de arquivo permitidos na conexão inicial.
* **Rejeição Inteligente**: Rejeita arquivos do cliente que não correspondam às extensões configuradas, enviando uma notificação de erro.
* **Log Completo**: Possui uma aba de log (`LogPanel`) que registra todas as ações, conexões e transferências.
* **Salvamento de Arquivos**: Salva os arquivos recebidos na pasta `received_files/`.

### Cliente (`ClientGUI.java`)
* **Conexão Controlada**: Recebe a lista de extensões permitidas diretamente do servidor após a conexão.
* **Filtro de Arquivos na GUI**: O seletor de arquivos (`JFileChooser`) do cliente é filtrado para mostrar **apenas** os tipos de arquivos que o servidor aceita.
* **Transferência Mista**: Suporta envio de mensagens de texto e arquivos binários.
* **Interface Amigável**: Design visual limpo usando componentes Swing.
* **Log de Cliente**: Possui sua própria aba de log (`LogPanel`) para rastrear o status da conexão e transferências.

---

## 💻 Tecnologias Utilizadas

| Tecnologia | Descrição |
| :--- | :--- |
| **Java** | Linguagem principal do projeto. |
| **Swing** | Utilizada para construir as interfaces gráficas (GUI). |
| **Sockets (`java.net`)** | Comunicação Cliente-Servidor de baixo nível. |
| **Object Streams** | Utilizados para serializar e enviar dados customizados (objetos) pela rede. |

---

## 📁 Estrutura do Projeto

O projeto é modular e organizado. Para compilação e execução, todos os arquivos `.java` devem estar no mesmo diretório.

---

## ⚙️ Como Rodar o Projeto

### Pré-requisitos
* **JDK (Java Development Kit) 8** ou superior instalado.

### Passos de Execução

1.  Navegue até o diretório do projeto no seu terminal.
2.  Compile todos os arquivos Java de uma vez:
    ```bash
    javac *.java
    ```
3.  Crie o diretório de arquivos recebidos (se ainda não existir):
    ```bash
    mkdir received_files
    ```
4.  Execute o Servidor e o Cliente:
    * Se você tiver uma classe `MainApp` que inicia ambos:
        ```bash
        java MainApp
        ```
    * Para iniciar separadamente (recomendado em ambientes de teste):
        ```bash
        # 1. Inicie o Servidor primeiro
        java ServerGUI
        
        # 2. Em um terminal separado, inicie o Cliente
        java ClientGUI
        ```

### Sequência de Uso

1.  **Iniciar Servidor**: Na janela do Servidor, defina as extensões (ex: `txt,png`) e clique em "**Iniciar Servidor**".
2.  **Conectar Cliente**: Na janela do Cliente, clique em "**Conectar**". O log do cliente será atualizado com as extensões permitidas.
3.  **Enviar Arquivo**: No Cliente, clique em "**Selecionar Arquivo**" e escolha um arquivo válido.
4.  **Verificar**: O log do Servidor mostrará a transferência, e o arquivo será salvo na pasta local `received_files/`.
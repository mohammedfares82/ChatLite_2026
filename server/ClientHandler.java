package server;
import java.io.*;
import java.net.Socket;
public class ClientHandler extends Thread {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private String currentRoom;
    public ClientHandler(Socket socket) throws Exception {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }
    public void run() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {
                String[] parts = msg.split(" ", 3);
                String command = parts[0].toUpperCase();
                switch (command) {
                    case "HELLO":
                        String requestedUsername = parts[1];
                        String providedPassword  = parts.length >= 3 ? parts[2] : "";
                        if (!UserManager.isRegistered(requestedUsername)) {
                            out.println("403 NOT REGISTERED");
                            socket.close();
                            return;
                        }
                        if (!UserManager.checkPassword(requestedUsername, providedPassword)) {
                            out.println("401 WRONG PASSWORD");
                            socket.close();
                            return;
                        }
                        boolean added = UserManager.addUser(
                                requestedUsername,
                                new ClientSession(requestedUsername, socket)
                        );
                        if (!added) {
                            out.println("409 USERNAME TAKEN");
                            socket.close();
                            return;
                        }
                        username = requestedUsername;
                        out.println("200 WELCOME");
                        break;
                    case "JOIN":
                        if (currentRoom != null) {
                            RoomManager.leaveRoom(currentRoom, username);
                        }
                        currentRoom = parts[1];
                        RoomManager.joinRoom(currentRoom, username);
                        out.println("210 JOINED " + currentRoom);
                        break;
                    case "MSG":
                        if (currentRoom != null && parts.length >= 3) {
                            RoomManager.broadcast(parts[1], username, parts[2]);
                            out.println("211 SENT");
                            ClientSession senderSession = UserManager.getUser(username);
                            if (senderSession != null) senderSession.incrementSent();
                        }
                        break;
                    case "PM":
                        ClientSession target = UserManager.getUser(parts[1]);
                        if (target != null) {
                            PrintWriter targetOut = new PrintWriter(
                                    target.getConnectionSocket().getOutputStream(), true
                            );
                            targetOut.println("(PM) " + username + ": " + parts[2]);
                            out.println("212 PRIVATE SENT");
                            ClientSession pmSender = UserManager.getUser(username);
                            if (pmSender != null) pmSender.incrementSent();
                            target.incrementInbox();
                        } else {
                            out.println("404 USER NOT FOUND");
                        }
                        break;
                    case "USERS":
                        out.println("213 " + UserManager.getAllRegistered().size());
                        for (String u : UserManager.getAllRegistered()) {
                            ClientSession cs = UserManager.getUser(u);
                            String status = cs != null ? cs.getStatus() : "Offline";
                            out.println("213U " + u + " " + status);
                        }
                        out.println("213 END");
                        break;
                    case "ROOMS":
                        for (String r : RoomManager.getRooms().keySet()) {
                            out.println("214 " + r);
                        }
                        break;
                    case "LEAVE":
                        if (parts.length >= 2) {
                            String roomToLeave = parts[1];
                            RoomManager.leaveRoom(roomToLeave, username);
                            if (roomToLeave.equals(currentRoom)) {
                                currentRoom = null;
                            }
                            out.println("215 LEFT " + roomToLeave);
                        }
                        break;
                    case "QUIT":
                        if (currentRoom != null) {
                            RoomManager.leaveRoom(currentRoom, username);
                            currentRoom = null;
                        }
                        UserManager.removeUser(username);
                        UserManager.unregisterUsername(username);
                        out.println("221 BYE");
                        socket.close();
                        return;
                    case "STATUS":
                        if (parts.length >= 2) {
                            String newStatus = parts[1];
                            ClientSession session = UserManager.getUser(username);
                            if (session != null) {
                                session.setStatus(newStatus);
                                out.println("200 STATUS " + newStatus);
                                if (currentRoom != null) {
                                    RoomManager.broadcast(currentRoom, "System",
                                            username + " is now " + newStatus);
                                }
                            }
                        }
                        break;
                }
            }
        } catch (Exception e) {
            System.out.println("Client disconnected: " + username);
        } finally {
            try {
                if (username != null) UserManager.removeUser(username);
                if (currentRoom != null) RoomManager.leaveRoom(currentRoom, username);
                if (!socket.isClosed()) socket.close();
            } catch (Exception ignored) {}
        }
    }
}
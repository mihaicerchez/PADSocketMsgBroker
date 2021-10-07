package server;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class Receiver implements Runnable {

    private final User user;
    private final Server server;
    private String room = "";
    private String nextRoom = "";
    private JSONObject jsonMsgHistory = new JSONObject();
    private String messageHistory = "{\"nextRoom\":\"\",\"room\":\"Home\"}";
    //private List<String> messages = new ArrayList<>();
    Map messages=new HashMap();
    /**
     * @param server current server object
     * @param user   current user connection
     */
    public Receiver(Server server, User user) {
        this.server = server;
        this.user = user;
    }

    /**
     * Wait for user to send message and show it to other users
     */
    @Override
    public void run() {
        try {
            JSONObject jsonReturn = null;
            Scanner in = new Scanner(this.user.getInputStream());
            // While socket connection is still active
            while (!this.user.isSocketClosed()) {
                if (in.hasNextLine()) {
                    // Decode JSON created by user
                    JSONObject json = (JSONObject) new JSONParser().parse(in.nextLine());

                    String message = (String) json.get("message");

                    // Send message
                    Command command = new Command(message, this.server);
                    if (command.isCommand()) {

                        if (command.roomChange()) {// Get second word of command to change room name
                            try {
                                this.nextRoom = this.room;
                                this.room = message.split(" ")[1];

                                // Update user room
                                if (this.nextRoom == null || this.nextRoom.equals("Home")) {
                                    this.user.setRoom(this.room);
                                    jsonReturn = new JSONObject();
                                    jsonReturn.put("room", this.room);
                                    System.out.println(jsonReturn);
                                } else {
                                    this.user.setRoom(this.room, this.nextRoom);
                                    jsonReturn = new JSONObject();;
                                    jsonReturn.put("room", this.room);
                                    jsonReturn.put("nextRoom", this.nextRoom);
                                    System.out.println(jsonReturn);
                                }
                                sendMessage(jsonReturn.toString(), this.user);

                                // Show join message
                                for (User thread : this.server.getThreadsByRoom(this.room)) {
                                    jsonReturn = new JSONObject();
                                    jsonReturn.put("message", this.user.getUsername() + " joined room");
                                    sendMessage(jsonReturn.toString(), thread);
                                    sendLastMessages(thread);
                                }
                            } catch (ArrayIndexOutOfBoundsException e) {
                                jsonReturn = new JSONObject();
                                jsonReturn.put("message", "You are currently in room: " + this.room);
                                sendMessage(jsonReturn.toString(), this.user);
                            }
                        } else if (command.isDM()) {
                            try {
                                String[] splitMessage = message.split(" ");
                                String userMessage = splitMessage[2];
                                jsonReturn = new JSONObject();
                                User selectedUser = this.server.getThreadByName(splitMessage[1]);
                                if (selectedUser != null) {
                                    jsonReturn.put("message", userMessage);
                                    sendMessage(jsonReturn.toString(), selectedUser);
                                } else {
                                    jsonReturn.put("message", "User does not exist or is not online");
                                    sendMessage(jsonReturn.toString(), this.user);
                                }
                            } catch (ArrayIndexOutOfBoundsException e) {
                                jsonReturn = new JSONObject();
                                jsonReturn.put("message", "Invalid dm format. Correct format: /dm {user_name} {message}");
                                sendMessage(jsonReturn.toString(), this.user);
                            }
                        } else {// Server message
                            jsonReturn = new JSONObject();
                            jsonReturn.put("message", command.getMessage());
                            sendMessage(jsonReturn.toString(), this.user);
                        }
                    } else {
                        // Normal message
                        if (this.room.equals("")) {
                            jsonReturn = new JSONObject();
                            jsonReturn.put("message", "You're currently in no room, change room by typing /room {room_name})");
                            sendMessage(jsonReturn.toString(), this.user);
                        } else {
                            String date = new SimpleDateFormat("h:mm a").format(new Date());
                            for (User thread : this.server.getThreadsByRoom(this.room)) {
                                jsonReturn = new JSONObject();
                                jsonReturn.put("username", this.user.getUsername() + " (room: " + room + ")");
                                jsonReturn.put("user_color", this.user.getColor());
                                jsonReturn.put("message", message);
                                jsonReturn.put("time", date);
                                sendMessage(jsonReturn.toString(), thread);
                                messages.put(thread, jsonReturn.toString());

                            }
                        }
                    }
                }
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param message message to send
     * @param thread  to what user/thread the message should be send
     */
    private void sendMessage(String message, User thread) {
        PrintStream userOut = thread.getOutStream();
        if (userOut != null) {
            userOut.println(message);
            userOut.flush();
        }
    }

    public void sendLastMessages(User thread) {
        Set set = messages.entrySet();//Converting to Set so that we can traverse
        for (Object o : set) {
            //Converting to Map.Entry so that we can get key and value separately
            Map.Entry entry = (Map.Entry) o;
            PrintStream userOut = thread.getOutStream();
            if (userOut != null) {
                userOut.println(entry.getValue());
                userOut.flush();
            }
            System.out.println(entry.getKey() + " " + entry.getValue());
        }
    }
}
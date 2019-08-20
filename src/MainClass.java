import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class MainClass {
	private ServerSocket server;
	static private HashMap<String, Integer> connectUsers = new HashMap<String, Integer>(); // 접속자 저장객체<userNo,socket>
	static private HashMap<String, String> connectUsers_roomId = new HashMap<String, String>(); // 접속자
																								// 저장객체<userNo,roomId>
//	static private HashMap<Integer,UserItem> inRoom = new HashMap<Integer,UserItem>(); //방참여자 저장객체<roomId,UserItem>
//	private ArrayList<UserClass> arr_inRoom = new ArrayList<UserClass>();// 방참여자유저리스트 
	private HashMap<String, String> map_inRoom = new HashMap<String, String>();// (value:방id, key:유저no)
//	static private ArrayList<UserClass> arr_userlist = new ArrayList<UserClass>();// 방참여자유저리스트
//	UserItem userItem = new UserItem();//참여자:no,name,picture

	// 사용자 객체들을 관리하는 ArrayList
	ArrayList<UserClass> user_list;

	public static void main(String[] args) {
		// TODO Auto-generated method stub

		new MainClass();
	}

	// 메인메소드가 static으로 되어있기 때문에 다른것들을 다 static 으로 하기 귀찮기 때문에
	// 따로 생성자를 만들어서 진행 - > 메인에서는 호출정도의 기능만 구현하는게 좋다.
	public MainClass() {
		try {
			user_list = new ArrayList<UserClass>();
			// 서버 가동
			server = new ServerSocket(10502);
			// 사용자 접속 대기 스레드 가동
			ConnectionThread thread = new ConnectionThread();
			thread.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// 사용자 접속 대기를 처리하는 스레드 클래스
	class ConnectionThread extends Thread {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			try {
				while (true) {
					System.out.println("사용자 접속 대기");
					Socket socket = server.accept();// 만약 사용자가 접속하면,
					System.out.println("socket:" + socket);
					System.out.println("사용자가 접속하였습니다.");
					// 접속자 정보를 처리하는 스레드 가동
					NickNameThread thread = new NickNameThread(socket);
					thread.start();

				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	// 접속자정보 입력처리 스레드
	class NickNameThread extends Thread {
		private Socket socket;

		public NickNameThread(Socket socket) {
			this.socket = socket;
		}

		public void run() {
			try {
				// 1.스트림 추출
				InputStream is = socket.getInputStream();
				OutputStream os = socket.getOutputStream();
				DataInputStream dis = new DataInputStream(is);
				DataOutputStream dos = new DataOutputStream(os);

				// 2.접속자 정보 수신
				String connUser = dis.readUTF();
				System.out.println("접속자정보:" + connUser);
				JSONParser parser = new JSONParser();
				Object obj = parser.parse(connUser);
				JSONObject userInfo = (JSONObject) obj;
				String userNo = (String) userInfo.get("userNo");
				String userName = (String) userInfo.get("userName");
				String roomId = (String) userInfo.get("roomId");
				String first = (String) userInfo.get("first");
				System.out.println("접속자 정보(userNo):" + userNo);
				System.out.println("접속자 정보(userName):" + userName);
				System.out.println("접속자 정보(roomId):" + roomId);
				System.out.println("접속자 정보(first):" + first);

				// 3.접속자id,socket을 접속자HashMap에 저장한다. + 접속자id,룸id를 저장한다.
				connectUsers.put(userNo, socket.getPort());
				connectUsers_roomId.put(userNo, roomId);

				// 4.환영 메세지를 전달한다.+메시지 DB저장
				String who1 = "notice";
				String msg1 = "［" + userName + "］님 환영합니다.";
				JSONObject jsonData1 = new JSONObject();
				jsonData1.put("who", who1);
				jsonData1.put("message", msg1);

				if (first.equals(true)) {//유저가 방에 첫입장하는 경우, 
					dos.writeUTF(jsonData1.toJSONString());
					System.out.println(userName + "접속자에게 환영메세지 전달");
					saveNotice(roomId, who1, msg1, userNo);// 메세지저장: 방id,notice,환영메세지,userno
				}

				// 6.접속자를 관리하는 객체를 생성한다.
				UserClass user = new UserClass(userNo, socket);
				user.start();
				user_list.add(user);

				// 5.기 접속자들 중, 방금 들어온 사용자와 같은 방에 있는 접속자들에게 '접속알림메세지'를 전달한다.+메세지 DB저장
				// 5-1.방id를 이용해 채팅방에 참여중인 유저리스트를 불러온다.(from JDBC)
				// 5-2.모든 접속자 리스트를 불러온다.
				// 5-3.모든 접속자들 중 현접속자와 같은 방id를 가진 사람들을 불러서 유저id를 array에 담는다.
				// 5-3.해당사람들에게 '접속메세지'를 전달한다.

				getInRoomMembers(roomId);// 방참여자를 불러와서 객체저장 (객체명:arr_inRoom)
				// 같은방에 있는 접속자들만 찾아야함!!!
				Set set = connectUsers_roomId.keySet(); // 방아이디를 가져옴..
				Iterator iterator = set.iterator();
				String roomId_conn = null;
				ArrayList sameRoomUsers = new ArrayList();
				while (iterator.hasNext()) {
					String key = (String) iterator.next();// 유저No추출. - 방상관없이 접속되있는 사람모두!!!
					System.out.println("접속자key:" + key);
					roomId_conn = connectUsers_roomId.get(key);// 접속되어있는 사람들의 id를 품고있어.
					System.out.println("접속자의 방id 추출:" + roomId_conn);

					// 방참여자들과 접속자가들어가있는방id가 일치하는가?
					if (roomId_conn.equals(roomId)) {
						sameRoomUsers.add(key);
						System.out.println("앱 접속자들의 방 id = 현재 접속자의 방 id    유저id를 저장!!");
					} else {// 일치하지 않을 경우, 접속메세지를 전달하지 않는다.
						System.out.println("앱 접속자들의 방 id != 현재 접속자의 방 id    유저id를 저장안함!!");
					}
				}
				String who2 = null, msg2 = null;
				for (int i = 0; i < sameRoomUsers.size(); i++) {
					String nono = sameRoomUsers.get(i).toString();// 접속자들 중 같은방에 있는 유저no
					System.out.println("접속자들 중" + roomId + "번 방에 있는 유저no:" + nono);
					// 소켓 포트번호를 보내준다.
					Integer port = connectUsers.get(nono);
					System.out.println("소켓포트번호:" + connectUsers.get(nono));
					// 접속메세지 전달!!
					who2 = "notice";
					msg2 = "［" + userName + "］님이 입장했습니다.";
					JSONObject jsonData2 = new JSONObject();
					jsonData2.put("who", who2);
					jsonData2.put("message", msg2);

					if (first.equals(true)) { //유저가 방에 첫입장하는 경우, 
						sendToClientNotice(jsonData2.toJSONString(), port);
						System.out.println("접속메세지 전달(message):" + jsonData2.toJSONString());
						System.out.println("접속메세지 전달(key):" + nono);

						saveNotice(roomId, who2, msg2, nono);// 메세지저장: 방id,notice,접속메세지,userNo
						// 접속자가 아닌 사용자에게도 메세지 저장.
					}
				}

//				//방참여자랑, 방참여접속자랑 일치하지 않으면 메세지 저장.
//				//참여자id랑, 접속자 id랑 일치하지 않으면, 메세지 저장하는거임 ! 
//				Set set1 = map_inRoom.keySet(); // 방아이디를 가져옴..
//				System.out.println("keyset:"+set1);
//				Iterator iterator1 = set1.iterator();
//				while (iterator1.hasNext()) {
//					String nono = null;
//					String key = (String) iterator1.next();// 유저No추출. - 방상관없이 접속되있는 사람모두!!!
//					System.out.println("접속자key:" + key);
//					for(int i = 0; i<sameRoomUsers.size(); i++) {
//						nono = sameRoomUsers.get(i).toString();// 접속자들 중 같은방에 있는 유저no
//						System.out.println("접속자들 중" + roomId + "번 방에 있는 유저no:" + nono);
//					}
//					if(!key.equals(nono)) {
//						saveNotice(roomId,who2,msg2,key);//메세지저장: 방id,notice,접속메세지,userNo
//					}
//				}
//				map_inRoom.clear();
//				sameRoomUsers.clear();
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

		// 방에 속한 멤버정보 불러오기
		public void getInRoomMembers(String roomId) throws SQLException, ClassNotFoundException {
			String host = "jdbc:mysql://13.209.108.67:3306/project?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";
			String user = "root";
			String pw = "Root1234#";
			Class.forName("com.mysql.cj.jdbc.Driver");
			Connection conn = DriverManager.getConnection(host, user, pw);
			Statement stmt = conn.createStatement();
//					String sql = "SELECT * from chat_participants where FK_room_id="+roomId+"";
			String sql = "SELECT p.FK_room_id, p.FK_user_id, m.name, m.picture FROM chat_participants p JOIN member_info m ON p.FK_user_id=m.no WHERE p.FK_room_id="
					+ roomId + "";
			ResultSet rs = stmt.executeQuery(sql);
			while (rs.next()) {
				String rId = rs.getString("FK_room_id");
				String uId = rs.getString("FK_user_id");
				String uName = rs.getString("name");
				String uPicture = rs.getString("picture");
				System.out.println("방번호:" + rId);
				System.out.println("유저넘버:" + uId);
				System.out.println("유저이름:" + uName);
				System.out.println("유저이미지:" + uPicture);
				UserClass userItem = new UserClass(uId, uName, uPicture);//
				map_inRoom.put(uId, rId);// 방참여자 객체에 추가.
//				arr_inRoom.add(userItem);// 방참여자 객체에 추가.
			}

			System.out.println("JDBC: 방참여인원 불러오기 - OKKK");
			conn.close();
		};

		// 환영 or 접속메세지 DB저장
		public void saveNotice(String roomId, String who, String msg, String userNo)
				throws ClassNotFoundException, SQLException {
//			long time = System.currentTimeMillis(); 
//			SimpleDateFormat dayTime = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
//			String str = dayTime.format(new Date(time));
//			System.out.println("현재시간:"+str);
//			
			String host = "jdbc:mysql://13.209.108.67:3306/project?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";
			String user = "root";
			String pw = "Root1234#";
			Class.forName("com.mysql.cj.jdbc.Driver");
			Connection conn = DriverManager.getConnection(host, user, pw);
			Statement stmt = conn.createStatement();
			String sql = "INSERT INTO chat_message(`FK_room_id`,`who`,`message`,`userNo`) VALUES(" + roomId + ",'" + who
					+ "','" + msg + "','" + userNo + "')";
			System.out.println(sql);
			System.out.println(msg);

			int rs = stmt.executeUpdate(sql);
			System.out.println(rs);
			System.out.println("JDBC: notice - 저장OK");

			conn.close();
		};

	}

	// 사용자 정보를 관리하는 클래스
	class UserClass extends Thread {
//			Integer userNo;
		Socket socket;
		DataInputStream dis;
		DataOutputStream dos;
		private String roomId;
		private String userNo;
		private String userName;
		private String userImage;

		public UserClass() {
			super();
		}

		public UserClass(String userNo, String userName, String userImage) {
			super();
			this.userNo = userNo;
			this.userName = userName;
			this.userImage = userImage;
		}

//			public Integer getRoomId() {
//				return roomId;
//			}
//			public void setRoomId(Integer roomId) {
//				this.roomId = roomId;
//			}
		public String getUserNo() {
			return userNo;
		}

		public void setUserNo(String userNo) {
			this.userNo = userNo;
		}

		public String getUserName() {
			return userName;
		}

		public void setUserName(String userName) {
			this.userName = userName;
		}

		public String getUserImage() {
			return userImage;
		}

		public void setUserImage(String userImage) {
			this.userImage = userImage;
		}

		public UserClass(String userNo, Socket socket) {
			try {
				this.userNo = userNo;
				this.socket = socket;
				InputStream is = socket.getInputStream();
				OutputStream os = socket.getOutputStream();
				dis = new DataInputStream(is);
				dos = new DataOutputStream(os);

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// 사용자로부터 메세지를 수신받는 스레드
		public void run() {
			try {
				while (true) {
					// 1. 클라이언트에게 메세지를 수신받는다.
					String msg = dis.readUTF();
					System.out.println("받은메세지:" + msg);
					JSONParser parser = new JSONParser();
					Object obj = parser.parse(msg);
					JSONObject receiveMessage = (JSONObject) obj;
					String kindof = (String) receiveMessage.get("kindof"); // 유저message
					System.out.println("받은 메세지관련 정보(kindof):" + kindof);
					if (kindof.equals("out")) { // 이건 방을 나갔다는
												// 말임./////////////////////////////////////////////////////////////////////////
						/*
						 * 방나가기 1. roomId와 userNo를 받는다. 2. 방에들어간 접속자 맵에서 해당유저 삭제 - connectUsers_roomId
						 * 3. 접속자목록에서 해당 userNo키 삭제 - connectUsers 4. roomId에 참여중인 접속자들에게 나갔습니다라는 문구
						 * 보내주기 5. 방에 있는 사람들,, 나갔습니다 문구 저장하기
						 */
						String roomId = (String) receiveMessage.get("roomId"); // 방id
						String userNo = (String) receiveMessage.get("userNo");
						String userName = (String) receiveMessage.get("userName"); // 유저name
						System.out.println("방나가기 정보(roomId):" + roomId);
						System.out.println("방나가기 정보(userNo):" + userNo);
						System.out.println("받은 메세지관련 정보(userName):" + userName);

						Set set = connectUsers_roomId.keySet();
						Iterator iterator = set.iterator();
						while (iterator.hasNext()) {
							String key = (String) iterator.next();
							System.out.println("현 접속자key:" + key);
							if (key.equals(userNo)) {
								connectUsers_roomId.remove(key);
								set = connectUsers_roomId.keySet();
								iterator = set.iterator();
								System.out.println(userNo + "번유저 방참여 접속자목록에서 삭제:" + set);
							}
						}

						Set set2 = connectUsers.keySet();
						Iterator iterator2 = set2.iterator();
						while (iterator2.hasNext()) {
							String key = (String) iterator2.next();
							System.out.println("현 접속자key:" + key);
							if (key.equals(userNo)) {
								connectUsers.remove(key);
								set2 = connectUsers.keySet();
								iterator2 = set2.iterator();
								System.out.println(userNo + "번유저 접속자목록에서 삭제:" + set2);
							}
						}
						System.out.println(userNo + "번 유저 방나가기 완료");

						/////////////////////
						getInRoomMembers(roomId);// 방참여자를 불러와서 객체저장 (객체명:arr_inRoom)
						// 같은방에 있는 접속자들만 찾아야함!!!
						// 같은방에 있는 접속자들만 찾아야함!!!
						Set set3 = connectUsers_roomId.keySet(); // 방아이디를 가져옴..
						Iterator iterator3 = set3.iterator();
						String roomId_conn = null;
						ArrayList sameRoomUsers = new ArrayList();
						while (iterator3.hasNext()) {
							String key = (String) iterator3.next();// 유저No추출. - 방상관없이 접속되있는 사람모두!!!
							System.out.println("접속자key:" + key);
							roomId_conn = connectUsers_roomId.get(key);// 접속되어있는 사람들의 id를 품고있어.
							System.out.println("접속자의 방id 추출:" + roomId_conn);

							// 방참여자들과 접속자가들어가있는방id가 일치하는가?
							if (roomId_conn.equals(roomId)) {
								sameRoomUsers.add(key);
								System.out.println("앱 접속자들의 방 id = 현재 접속자의 방 id    유저id를 저장!!");
							} else {// 일치하지 않을 경우, 접속메세지를 전달하지 않는다.
								System.out.println("앱 접속자들의 방 id != 현재 접속자의 방 id    유저id를 저장안함!!");
							}
						}

						String who2 = null, msg2 = null;
						for (int i = 0; i < sameRoomUsers.size(); i++) {
							String nono = sameRoomUsers.get(i).toString();// 접속자들 중 같은방에 있는 유저no
							System.out.println("접속자들 중" + roomId + "번 방에 있는 유저no:" + nono);
							// 소켓 포트번호를 보내준다.
							Integer port = connectUsers.get(nono);
							System.out.println("소켓포트번호:" + connectUsers.get(nono));
							// 접속메세지 전달!!
							who2 = "notice";
							msg2 = "［" + userName + "］님이 방을 나갔습니다.";
							JSONObject jsonData2 = new JSONObject();
							jsonData2.put("who", who2);
							jsonData2.put("message", msg2);

							sendToClientNotice(jsonData2.toJSONString(), port);
							System.out.println("접속메세지 전달(message):" + jsonData2.toJSONString());
							System.out.println("접속메세지 전달(key):" + nono);

							saveNotice(roomId, who2, msg2, nono);// 메세지저장: 방id,notice,접속메세지,userNo
							// 접속자가 아닌 사용자에게도 메세지 저장.
						}

//						sendToClient(msg, port);

					} else if (kindof.equals("message")) {// 메세지일경우/////////////////////////////////////////////////////////////////////////
						String roomId = (String) receiveMessage.get("roomId"); // 방id
						String userNo = (String) receiveMessage.get("userNo"); // 유저no
						String userName = (String) receiveMessage.get("userName"); // 유저name
						String userImage = (String) receiveMessage.get("userImage"); // 유저image
						String message = (String) receiveMessage.get("message"); // 유저message
						System.out.println("받은 메세지관련 정보(roomId):" + roomId);
						System.out.println("받은 메세지관련 정보(userNo):" + userNo);
						System.out.println("받은 메세지관련 정보(userName):" + userName);
						System.out.println("받은 메세지관련 정보(userImage):" + userImage);
						System.out.println("받은 메세지관련 정보(message):" + message);

						// 2.기 접속자들 중, 방금 들어온 사용자와 같은 방에 있는 접속자들에게 메세지를 전송한다.+메세지DB저장
						// 2-1.방id를 이용해 채팅방에 참여중인 유저리스트를 불러온다.(from JDBC)
						// 2-2.모든 접속자들 중 방금 메세지를 보낸자와 같은 방id를 가진 사람들을 불러서 array에 담는다.
						// 2-3.그 array사람들에게 메세지를 전송한다.(전송정보:방id,보낸사람(id,이름,이미지),who,메세지,보낸시간)
						// 2-3-1.만약 보낸사람 id와 같으면, who=sender를
						// 같지 않으면, who=receiver를 저장 해서보낸다.
						// 2-4.그 사람들의 유no로 메세지를 저장한다 (저장정보:방id,보낸사람(id,이름,이미지),who,받을사람id,메세지,보낸시간)
						// 2-4-1. 만약 보낸사람 id와 같으면, who=sender를
						// 같지 않으면, who=receiver를 저장 해서보낸다.

						getInRoomMembers(roomId);// 방참여자를 불러와서 객체저장 (객체명:map_inRoom)
						// 같은방에 있는 접속자들만 찾아야함!!!
						Set set = connectUsers_roomId.keySet(); // 방아이디를 가져옴..
						Iterator iterator = set.iterator();
						String roomId_conn = null;
						ArrayList sameRoomUsers = new ArrayList();
						while (iterator.hasNext()) {
							String key = (String) iterator.next();// 유저No추출. - 방상관없이 접속되있는 사람모두!!!
							System.out.println("현 접속자key:" + key);
							roomId_conn = connectUsers_roomId.get(key);// 접속되어있는 사람들의 id를 품고있어.
							System.out.println("현 접속자의 방id 추출:" + roomId_conn);

							// 방참여자들과 접속자가들어가있는방id가 일치하는가?
							if (roomId_conn.equals(roomId)) {
								sameRoomUsers.add(key);
								System.out.println("메세지전송용: 앱 접속자들의 방 id = 현재 접속자의 방 id    유저id를 저장!!");
							} else {// 일치하지 않을 경우, 접속메세지를 전달하지 않는다.
								System.out.println("메세지전송용: 앱 접속자들의 방 id != 현재 접속자의 방 id    유저id를 저장안함!!");
							}
						}
//					

						String who = null;
						for (int i = 0; i < sameRoomUsers.size(); i++) {
							String nono = sameRoomUsers.get(i).toString();// 접속자들 중 같은방에 있는 유저no
							System.out.println("접속자들 중" + roomId + "번 방에 있는 유저no:" + nono);
							// 소켓 포트번호를 보내준다.
							Integer port = connectUsers.get(nono);
							System.out.println("소켓포트번호:" + connectUsers.get(nono));
							// 메세지 전달!!
							if (userNo.equals(nono)) {
								who = "sender";
							} else {
								who = "receiver";
							}
							// 시간 구하기
							long time = System.currentTimeMillis();
							SimpleDateFormat dayTime = new SimpleDateFormat("a h:mm");
							String str = dayTime.format(new Date(time));
							System.out.println("time:" + str);

							JSONObject jsonMessage = new JSONObject();
							jsonMessage.put("who", who);
							jsonMessage.put("roomId", roomId);
							jsonMessage.put("userId", userNo);
							jsonMessage.put("userName", userName);
							jsonMessage.put("userImage", userImage);
							jsonMessage.put("message", message);
							jsonMessage.put("time", str);// 시간
							sendToClient(jsonMessage.toJSONString(), port);

							System.out.println("접속메세지 전달(message):" + jsonMessage.toJSONString());
							System.out.println("접속메세지 전달(key):" + nono);

							// 메세지 저장(방참여자들 모두를 대상으로 저장.
							Set set1 = map_inRoom.keySet(); // 방아이디를 가져옴..
							System.out.println("keyset:" + set1);
							Iterator iterator1 = set1.iterator();
							while (iterator1.hasNext()) {

								String key = (String) iterator1.next();// 유저No추출. - 방상관없이 접속되있는 사람모두!!!
								System.out.println("접속자key:" + key);
								saveMessage(roomId, who, message, userNo, key, userName, userImage);// 메세지저장:
																									// 방id,who,메세지,보내는사람id,userNo,userName,userImage
							}
							map_inRoom.clear();// 같은방참여인원 지우기
						}
						sameRoomUsers.clear();// 같은방접속인원 지우기
					} else if (kindof.equals("image")) {// 이미지일
														// 경우//////////////////////////////////////////////////////////////////////////////
						String roomId = (String) receiveMessage.get("roomId"); // 방id
						String userNo = (String) receiveMessage.get("userNo"); // 유저no
						String userName = (String) receiveMessage.get("userName"); // 유저name
						String userImage = (String) receiveMessage.get("userImage"); // 유저image
//						String message = (String) receiveMessage.get("message"); // 유저message
						System.out.println("받은 메세지관련 정보(roomId):" + roomId);
						System.out.println("받은 메세지관련 정보(userNo):" + userNo);
						System.out.println("받은 메세지관련 정보(userName):" + userName);
						System.out.println("받은 메세지관련 정보(userImage):" + userImage);
//						System.out.println("받은 메세지관련 정보(message):" + message);

						JSONArray jsonImage = new JSONArray();
						// 이미지풀기
						try {
							jsonImage = (JSONArray) receiveMessage.get("message");
							for (int i = 0; i < jsonImage.size(); i++) {
								String image = (String) jsonImage.get(i);
								System.out.println("image : " + jsonImage.get(i));
							}
						} catch (Exception e) {
							e.printStackTrace();
						}

						// 2.기 접속자들 중, 방금 들어온 사용자와 같은 방에 있는 접속자들에게 메세지를 전송한다.+메세지DB저장
						// 2-1.방id를 이용해 채팅방에 참여중인 유저리스트를 불러온다.(from JDBC)
						// 2-2.모든 접속자들 중 방금 메세지를 보낸자와 같은 방id를 가진 사람들을 불러서 array에 담는다.
						// 2-3.그 array사람들에게 메세지를 전송한다.(전송정보:방id,보낸사람(id,이름,이미지),who,메세지,보낸시간)
						// 2-3-1.만약 보낸사람 id와 같으면, who=sender를
						// 같지 않으면, who=receiver를 저장 해서보낸다.
						// 2-4.그 사람들의 유no로 메세지를 저장한다 (저장정보:방id,보낸사람(id,이름,이미지),who,받을사람id,메세지,보낸시간)
						// 2-4-1. 만약 보낸사람 id와 같으면, who=sender를
						// 같지 않으면, who=receiver를 저장 해서보낸다.

						getInRoomMembers(roomId);// 방참여자를 불러와서 객체저장 (객체명:map_inRoom)
						// 같은방에 있는 접속자들만 찾아야함!!!
						Set set = connectUsers_roomId.keySet(); // 방아이디를 가져옴..
						Iterator iterator = set.iterator();
						String roomId_conn = null;
						ArrayList sameRoomUsers = new ArrayList();
						while (iterator.hasNext()) {
							String key = (String) iterator.next();// 유저No추출. - 방상관없이 접속되있는 사람모두!!!
							System.out.println("현 접속자key:" + key);
							roomId_conn = connectUsers_roomId.get(key);// 접속되어있는 사람들의 id를 품고있어.
							System.out.println("현 접속자의 방id 추출:" + roomId_conn);

							// 방참여자들과 접속자가들어가있는방id가 일치하는가?
							if (roomId_conn.equals(roomId)) {
								sameRoomUsers.add(key);
								System.out.println("메세지전송용: 앱 접속자들의 방 id = 현재 접속자의 방 id    유저id를 저장!!");
							} else {// 일치하지 않을 경우, 접속메세지를 전달하지 않는다.
								System.out.println("메세지전송용: 앱 접속자들의 방 id != 현재 접속자의 방 id    유저id를 저장안함!!");
							}
						}
//					

						String who = null;
						for (int i = 0; i < sameRoomUsers.size(); i++) {
							String nono = sameRoomUsers.get(i).toString();// 접속자들 중 같은방에 있는 유저no
							System.out.println("접속자들 중" + roomId + "번 방에 있는 유저no:" + nono);
							// 소켓 포트번호를 보내준다.
							Integer port = connectUsers.get(nono);
							System.out.println("소켓포트번호:" + connectUsers.get(nono));
							// 메세지 전달!!
							if (userNo.equals(nono)) {
								who = "sender_image";
							} else {
								who = "receiver_image";
							}
							// 시간 구하기
							long time = System.currentTimeMillis();
							SimpleDateFormat dayTime = new SimpleDateFormat("a h:mm");
							String str = dayTime.format(new Date(time));
							System.out.println("time:" + str);

							JSONObject jsonMessage = new JSONObject();
							jsonMessage.put("who", who);
							jsonMessage.put("roomId", roomId);
							jsonMessage.put("userId", userNo);
							jsonMessage.put("userName", userName);
							jsonMessage.put("userImage", userImage);
							jsonMessage.put("message", jsonImage);
							jsonMessage.put("time", str);// 시간
							if (!userNo.equals(nono)) {
								sendToClient(jsonMessage.toJSONString(), port);
							}
							System.out.printf("저장이미지", jsonImage);
							System.out.println("접속메세지 전달(message):" + jsonMessage.toJSONString());
							System.out.println("접속메세지 전달(key):" + nono);

							// 메세지 저장(방참여자들 모두를 대상으로 저장.
							Set set1 = map_inRoom.keySet(); // 방아이디를 가져옴..
							System.out.println("keyset:" + set1);
							Iterator iterator1 = set1.iterator();
							while (iterator1.hasNext()) {
								String key = (String) iterator1.next();// 유저No추출. - 방상관없이 접속되있는 사람모두!!!
								System.out.println("접속자key:" + key);

								for (int j = 0; j < jsonImage.size(); j++) {
									String image = jsonImage.get(j).toString();
									System.out.printf("저장이미지:", image);
									saveMessage(roomId, who, image, userNo, key, userName, userImage);// 메세지저장:
									// 방id,who,메세지,보내는사람id,userNo,userName,userImage
								}
							}
							map_inRoom.clear();// 같은방참여인원 지우기
						}
						sameRoomUsers.clear();// 같은방접속인원 지우기

					}
				} // message is empty
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// 방에 속한 멤버정보 불러오기
		public void getInRoomMembers(String roomId) throws SQLException, ClassNotFoundException {
			String host = "jdbc:mysql://13.209.108.67:3306/project?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";
			String user = "root";
			String pw = "Root1234#";
			Class.forName("com.mysql.cj.jdbc.Driver");
			Connection conn = DriverManager.getConnection(host, user, pw);
			Statement stmt = conn.createStatement();
//							String sql = "SELECT * from chat_participants where FK_room_id="+roomId+"";
			String sql = "SELECT p.FK_room_id, p.FK_user_id, m.name, m.picture FROM chat_participants p JOIN member_info m ON p.FK_user_id=m.no WHERE p.FK_room_id="
					+ roomId + "";
			ResultSet rs = stmt.executeQuery(sql);
			while (rs.next()) {
				String rId = rs.getString("FK_room_id");
				String uId = rs.getString("FK_user_id");
				String uName = rs.getString("name");
				String uPicture = rs.getString("picture");
				System.out.println("방번호:" + rId);
				System.out.println("유저넘버:" + uId);
				System.out.println("유저이름:" + uName);
				System.out.println("유저이미지:" + uPicture);
				UserClass userItem = new UserClass(uId, uName, uPicture);//
				map_inRoom.put(uId, rId);// 방참여자 객체에 추가.
			}

			System.out.println("JDBC: 방참여인원 불러오기 - OKKK");
			conn.close();
		};

		// 송신메세지 DB저장
		public void saveMessage(String roomId, String who, String msg, String sender, String userNo, String userName,
				String userImage) throws ClassNotFoundException, SQLException {

			String host = "jdbc:mysql://13.209.108.67:3306/project?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";
			String user = "root";
			String pw = "Root1234#";
			Class.forName("com.mysql.cj.jdbc.Driver");
			Connection conn = DriverManager.getConnection(host, user, pw);
			Statement stmt = conn.createStatement();
			String sql = "INSERT INTO chat_message(`FK_room_id`,`who`,`message`,`sender`,`userNo`,`userName`,`userImage`) VALUES("
					+ roomId + ",'" + who + "','" + msg + "'," + sender + "," + userNo + ",'" + userName + "','"
					+ userImage + "')";
			System.out.println(sql);
			System.out.println(msg);

			int rs = stmt.executeUpdate(sql);
			System.out.println(rs);
			System.out.println("JDBC: message - 저장OK");

			conn.close();
		};

		// 방 나가기 정보 저장하기
		public void saveNotice(String roomId, String who, String msg, String userNo)
				throws ClassNotFoundException, SQLException {

			String host = "jdbc:mysql://13.209.108.67:3306/project?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";
			String user = "root";
			String pw = "Root1234#";
			Class.forName("com.mysql.cj.jdbc.Driver");
			Connection conn = DriverManager.getConnection(host, user, pw);
			Statement stmt = conn.createStatement();
			String sql = "INSERT INTO chat_message(`FK_room_id`,`who`,`message`,`userNo`) VALUES(" + roomId + ",'" + who
					+ "','" + msg + "','" + userNo + "')";
			System.out.println(sql);
			System.out.println(msg);

			int rs = stmt.executeUpdate(sql);
			System.out.println(rs);
			System.out.println("JDBC: notice - 저장OK");

			conn.close();
		};

	}

	public synchronized void sendToClient(String msg, int port) {
		try {
			// 사용자의 수만큼 반복
			for (UserClass user : user_list) {// 접속된 유저리스트 중에, 포트번호가 일치하면
				if (user.socket.getPort() == port) {// 유저의port번호와 일치하면
					// 메세지를 해당클라이언트에게 전달한다.
					user.dos.writeUTF(msg);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized void sendToClientNotice(String msg, int port) {
		try {
			for (UserClass user : user_list) {// 접속된 유저리스트 중에, 포트번호가 일치하면
				if (user.socket.getPort() == port) {// 유저의port번호와 일치하면
					// 메세지를 해당클라이언트에게 전달한다.
					user.dos.writeUTF(msg);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public class JdbcConnect {

		public void getParticipants(Integer roomId) throws SQLException, ClassNotFoundException { // 채팅방 참여자 불러오기
			String host = "jdbc:mysql://13.209.108.67:3306/project?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";
			String user = "root";
			String pw = "Root1234#";
			Class.forName("com.mysql.cj.jdbc.Driver");
			Connection conn = DriverManager.getConnection(host, user, pw);
			Statement stmt = conn.createStatement();
			String sql = "SELECT * from chat_participants where FK_room_id=" + roomId + "";
			System.out.println(sql);
			ResultSet rs = stmt.executeQuery(sql);
			while (rs.next()) {
				Integer rId = rs.getInt("FK_room_id");
				Integer uId = rs.getInt("FK_user_id");
				System.out.println("방번호" + rId);
				System.out.println("유저넘버" + uId);
			}
			// connection객체를 생성해서 디비에 연결해줌..
			System.out.println("OKKK");
			conn.close();
		}
	}

}

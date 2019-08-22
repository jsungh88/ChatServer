
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class JdbcConnect {
	static String host = "jdbc:mysql://[host_address]:3306/[database_name]?useSSL=false&serverTimezone=UTC";
	static String user = "[user_id]";
	static String pw = "[password]";

	public void getParticipants(Integer roomId) throws SQLException, ClassNotFoundException { // 채팅방 참여자 불러오기
		Class.forName("com.mysql.cj.jdbc.Driver");
		Connection conn = DriverManager.getConnection(host, user, pw);
		Statement stmt = conn.createStatement();
		String sql = "SELECT * from chat_participants where FK_room_id="+roomId+"";
		System.out.println(sql);
		ResultSet rs = stmt.executeQuery(sql);
		while (rs.next()) {
			Integer rId = rs.getInt(1);
			Integer uId = rs.getInt(2);
			System.out.println("%d &s %d\n, FK_room_id, FK_user_id");
		}
		// connection객체를 생성해서 디비에 연결해줌..
		System.out.println("OKKK");
		conn.close();
	}
}

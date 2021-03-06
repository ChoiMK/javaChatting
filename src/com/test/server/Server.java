package com.test.server;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

public class Server extends JFrame implements ActionListener { //JFrame과 액션리스너를 상속   
	
	private JPanel contentPane;
	private JTextField port_tf;	//port 텍스트필드
	private JTextArea textArea = new JTextArea();
	JButton start_btn = new JButton("서버 실행");
	JButton stop_btn = new JButton("서버 중지");
	
	//Network 자원
	private ServerSocket serverSocket;
	private Socket socket;
	private int port;
	private Vector user_vc = new Vector();
	private StringTokenizer st;
	private Vector room_vc  = new Vector();
	
	Server(){ //생성자
		
		init(); //화면을 생성하는 메서드
		start(); //리스너 설정 메소드
	}
	
	private void start(){
		start_btn.addActionListener(this);
		stop_btn.addActionListener(this);
	}
	
	private void init(){ //화면구성
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 351, 385);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);
		
		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setBounds(12, 10, 311, 157);
		contentPane.add(scrollPane);
		
		
		scrollPane.setViewportView(textArea);
		
		JLabel label = new JLabel("포트 번호");
		label.setBounds(12, 208, 57, 15);
		contentPane.add(label);
		
		port_tf = new JTextField();
		port_tf.setBounds(70, 205, 253, 21);
		contentPane.add(port_tf);
		port_tf.setColumns(10);
		
		
		start_btn.setBounds(12, 263, 154, 23);
		contentPane.add(start_btn);
		
		
		stop_btn.setBounds(163, 263, 161, 23);
		contentPane.add(stop_btn);
		this.setVisible(true); //true : 화면에 보이게  false : 화면에 보이지 않게
	}
	
	private void serverStart(){
		try {
			serverSocket = new ServerSocket(port); 
		} catch (IOException e) {
			e.printStackTrace();
		} 
	if(serverSocket!=null){ //정상적으로 포트가 열렸을 경우
		connection();
	}
	
	}
	
	private void connection(){
		//1가지의 스레드에서는 1가지의 일만 처리할수있다.
		Thread th = new Thread(new Runnable() {
			
			@Override
			public void run() { //스레드에서 처리할 일을 기재한다.
				
				while(true){
				
				try {
					textArea.append("사용자 접속 대기중\n");
					socket = serverSocket.accept(); //사용자 접속대기 무한대기
					textArea.append("사용자 접속\n");
					
					UserInfo user = new UserInfo(socket);
					
					user.start(); //객체의 스레드 실행
					
				} catch (IOException e) {
					e.printStackTrace();
				} 
			}//whil문 끝
		 }
		});

		th.start();
	}
	
	
	
	public static void main(String[] args) {
		new Server();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == start_btn){
			System.out.println("서버 스타트 버튼 클릭");
			port = Integer.parseInt(port_tf.getText().trim());
			serverStart(); //소켓 생성 및 사용자 접속 대기
		}
		else if(e.getSource() == stop_btn){
			System.out.println("서버 스탑 버튼 클릭");
		}
	} // 액션 이벤트 끝
	
	class UserInfo extends Thread {
		private OutputStream os;
		private InputStream is;
		private DataOutputStream dos;
		private DataInputStream dis;

		private Socket user_socket;
		private String nickName;
		
		private boolean roomCh = true;

		UserInfo(Socket socket) { // 생성자 메서드
			this.user_socket = socket;
			userNetWork();
		}
	
		
		private void userNetWork(){	// 네트워크 자원설정
			try {
				is = user_socket.getInputStream();
				dis = new DataInputStream(is);
				os = user_socket.getOutputStream();
				dos = new DataOutputStream(os);
				nickName = dis.readUTF(); //사용자의 닉네임을 받는다.
				textArea.append(nickName+":사용자 접속!\n");
				
				//기존 사용자들에게 새로운 알림
				System.out.println("현재 접속된 사용자 수"+user_vc.size());
				
				broadCast("NewUser/"+nickName); // 기존사용자에게 자신을 알린다
				
				//자신에게 기존 사용자를 받아오는 부분
				for(int i=0; i<user_vc.size(); i++){
					UserInfo u = (UserInfo)user_vc.elementAt(i);
					send_Message("OldUser/"+u.nickName);
				}
				
				//자신에게 기존 방 목록을 받아오는 부분
				for(int i=0; i<room_vc.size(); i++){
					RoomInfo r = (RoomInfo)room_vc.elementAt(i);
					send_Message("OldRoom/"+r.room_name);
				}
				send_Message("room_list_update/ ");
				
				
					
				user_vc.add(this);//사용자에게 알린후 Vector에 자신을 추가
				
				broadCast("user_list_update/ ");
				
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}

		public void run() { // thread에서 처리할 내용
			while (true) {
				try {
					String msg = dis.readUTF();
					textArea.append(nickName + " : 사용자로부터 들어온 메세지 :" + msg + "\n");// 메세지수신
					inMessage(msg);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}//run 메서드 끝
		
		private void inMessage(String str){ //클라이언트로부터 들어오는 메세지 처리
			st = new StringTokenizer(str,"/");
			String protocol = st.nextToken();
			String message = st.nextToken();
			
			System.out.println("프로토콜 : " +protocol);
			System.out.println("메세지 : " +message);
			
			if(protocol.equals("Note")){
				//protocol = Note
				//message = user
				//note = 받는내용
				String note = st.nextToken();
				System.out.println("받는사람 : "+message);
				System.out.println("보낼내용 : "+note);
				
				//벡터에서 해당 사용자를 찾아서 메세지 전송
				for(int i=0; i<user_vc.size(); i++){
					UserInfo u = (UserInfo)user_vc.elementAt(i);
					
					if(u.nickName.equals(message)){
						u.send_Message("Note/"+nickName+"/"+note);
						// Note/User1/~~~~~
					}
					
				}
				
			}// if문끝
			else if(protocol.equals("CreateRoom")){
				//1.현재 같은 방이 존재 하는지 확인한다.
				for(int i=0;i<room_vc.size();i++){
					
					RoomInfo r = (RoomInfo)room_vc.elementAt(i);
					
					if(r.room_name.equals(message)){ //만들고자 하는 방이 이미 존재 할때
						send_Message("CreateRoomFail/ok");
						roomCh = false;
						break;
					}
				} // for 끝
				
				if(roomCh){ //방을 만들수있을때
					RoomInfo new_room = new RoomInfo(message, this);
					room_vc.add(new_room); //전체 방 벡터에 방을 추가
					send_Message("CreateRoom/"+message);
					
					broadCast("New_Room/"+message);
				}
				roomCh = true;
			}// else if 문 끝
			else if(protocol.equals("Chatting")){
				
				String msg = st.nextToken();
				
				for(int i=0; i<room_vc.size(); i++){
					RoomInfo r =(RoomInfo)room_vc.elementAt(i);
					
					if(r.room_name.equals(message)){ // 해당 방을 찾았을때
						r.broadCast_Room("Chatting/"+nickName+"/"+msg);
					}
				}
			}//else if문 끝
			else if(protocol.equals("JoinRoom")){
				for(int i=0; i<room_vc.size(); i++){
					RoomInfo r =(RoomInfo)room_vc.elementAt(i);
					if(r.room_name.equals(message)){
						
						//새로운 사용자를 알린다.
						r.broadCast_Room("Chatting/알림/*******"+nickName+"님이 입장하셨습니다*******");
						
						//사용자추가
						r.add_user(this);
						send_Message("JoinRoom/"+message);
					}
				}
			}
			
			
		}
		private void broadCast(String str){ //전체 사용자에게 메세지를 보내는부분
			for(int i=0; i<user_vc.size(); i++ ) {  
				UserInfo u = (UserInfo)user_vc.elementAt(i);
				u.send_Message(str);
			}
		}
		
		private void send_Message(String str){ //문자열을 받아서 전송
			try {
				dos.writeUTF(str);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	} //UserInfo class 끝

	class RoomInfo{
		private String room_name;
		private Vector room_user_vc = new Vector(); 
		
		public RoomInfo(String str, UserInfo u) {
			this.room_name = str;
			this.room_user_vc.add(u);
		}
		
		public void broadCast_Room(String str){ //현재 방의 모든 사람에게 알린다.
			for(int i=0; i<room_user_vc.size(); i++){
				UserInfo u = (UserInfo)room_user_vc.elementAt(i);
					u.send_Message(str);
				}
			}
		
		
		private void add_user(UserInfo u){
			this.room_user_vc.add(u);
		}
	
	}



}

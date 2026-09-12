package com.landrop;

import com.landrop.db.DatabaseManager;

public class Main {

	public static void main(String[] args) {
		System.out.println("Starting LANDrop...");
		
		DatabaseManager.initializeDatabase();
	}

}

package cmd;

import java.util.Scanner;
import java.util.ArrayList;
import java.sql.SQLException;

import dbase.CID;
import dbase.Relations;

// Make a toggle so that only open accounts will be shown on a seek
// Make get have subcommands for getting account info, transactions, cards

// Creation of a new account
// Modification of a new account to add a new owner or make a deposit, withdrawal, or transfer.
// --- Removal of owners is also possible if it wouldn't leave an account with 0 owners
// Close an account
// Manage cards by adding a new card, or closing a card
//   Sub-sub-commands for card management

public class CmdAccount extends Command {
  public void execute() {
    boolean loop = hasScanner();

    // Show a list of commands
    if (loop) { subHelp(); }

    // Loop retrieval of commands regarding accounts
    while(loop) {
      String cmd = prompt("ACCT");

      switch(cmd.toLowerCase()) {
        case "new": subNew(); break;
        case "get": subGet(); break;
        case "seek": subSeek(); break;
        case "help": subHelp(); break;
        default:
          if (Command.isReturn(cmd.toLowerCase()))
            loop = false;
          else
            System.out.println("Command doesn't exist; use 'help' for a list");
      }
    }
  }

  /** subHelp() prints a list of subcommands for this command */

  private void subHelp() {
    System.out.println(
      "Subcommands for Account\n"+
      "---------------------------------------------------------------------\n"+
      "new        Create a new account for a customer\n"+
      "seek       Seek information about an account\n"+
      "get        Get information about an account\n"+
      "help       Show this list of subcommands\n"+
      "ret        Return to the main menu"
    );
  }

  /** subNew() allows the user to create a new account following this procedure:
    * 
    * <ul>
    *  <li>Ask if the customer is a new customer
    *  <ul>
    *   <li>If the customer is new, execute functionality to make a new customer
    *   <li>If the customer isn't new, ask for the name to associate with
    *   <ul>
    *    <li>If multiple customers share the name, then prompt for specific ID
    *   </ul>
    *  </ul>
    *  <li>Enter properties for the account
    *  <li>Insert the account into the database, and make an entry in the
    *      Account_Owner relation
    * </ul> */

  private void subNew() {
    try {
      // Ask if the customer is new
      System.out.println("Is this account for a present/returning customer? (y/n)");
      String isNew, cid = "";
      do { isNew = prompt("ACCT > NEW > Returning").toLowerCase(); }
      while(!isYes(isNew) && !isNo(isNew));

      // If the customer is new, generate a new customer entry in the DB
      // If the customer isn't new, get a customer ID
      if (isYes(isNew)) {
        // Execute CmdCustomer's function to generate a new customer
        System.out.println("Executing customer addition...\n");
        CmdCustomer cust = new CmdCustomer();
        cust.setCon(this.con);
        cust.setScanner(this.scan);

        if (!cust.subNewPub())
          throw new Exception("Customer generation failed");
        else
          System.out.println();

        // Get the new customer's CID
        ps = con.genQuery("SELECT COUNT (*) AS \"NEWID\" FROM \"Customer\"");
        rs = ps.executeQuery();
        rs.next();
        cid = rs.getString("NEWID");
        rs.close();
        ps.close();
      }
      else {
        ArrayList<String> dat = new ArrayList<>();
        String[] name;
        int res = 0;

        // Get the name of the customer
        System.out.println("Who is this account for? (Type a first, last name)");
        do { cid = prompt("ACCT > NEW > CID"); }
        while(!cid.contains(" "));
        name = cid.replace("'", "''").trim().split(" ");

        // Fetch results from the database
        ps = con.genQuery(
          "SELECT \"CID\", \"Con_Phone\", \"SSN\" FROM \"Customer\" WHERE "+
          "\"Fname\" = '"+name[0]+"' AND \"Lname\" = '"+name[1]+"'"
        );
        rs = ps.executeQuery();
        while(rs.next()) {
          res++;
          cid = rs.getString("CID");
          dat.add(String.format("(%4s) %s %s", rs.getString("CID"),
            rs.getString("Con_Phone"), rs.getString("SSN")));
        }
        rs.close();
        ps.close();

        // Show results and prompt if there is more than one for a CID
        if (res == 0)
          throw new Exception("No customer exists with the given name");
        else if (res > 1) {
          System.out.println("Which of these customers is it? (CID, Phone, SSN)");
          for(String cst : dat) { System.out.println(cst); }
          cid = prompt("ACCT > NEW > CID");
        }
      }

      // The user must enter these properties: Account type, balance, int rate,
      // compound rate, and monthly fee
      String[][] fields = Relations.getProps("ACCT > NEW",
        new String[][] {Relations.TBL_ACCOUNT[1], Relations.TBL_ACCOUNT[4],
        Relations.TBL_ACCOUNT[5], Relations.TBL_ACCOUNT[6],
        Relations.TBL_ACCOUNT[7]}, false);
      
      // <GENERATE QUERY AND INSERT INTO DATABASE>
      // <INSERT INTO ACCOUNT_OWNER>
    }
    catch(Exception e) {
      System.out.println("Error: "+e.getMessage());
    }
  }

  /** isYes(str) returns whether the given string is a 'yes' answer.
    * 
    * @param str The string to test
    * @return True if str is yes, y, t, or true */

  private boolean isYes(String str) {
    return str.equals("y") || str.equals("yes") || str.equals("t") || str.equals("true");
  }

  /** isNo(str) returns whether hte given string is a 'no' answer.
    *
    * @param str The string to test
    * @return True if str is no, n, f, or false */

  private boolean isNo(String str) {
    return str.equals("n") || str.equals("no") || str.equals("f") || str.equals("false");
  }

  /** subGet() allows the user to get information about an account, following
    * the following procedure:
    *
    * <ul>
    *  <li>Get a comma delimited list of account IDs to get information for
    *  <li>Show a list of information for each account, or error if the account
    *      doesn't exist in the database
    *  <ul>
    *   <li>Basic account info: AID, type, open/close date, and balance
    *   <li>Customer ID and name of owner, and phone number. There may be many
    *       owners listed for an account
    *   <li>Most recent card number, PIN, CSC, expiration date (for CHK accts)
    *  </ul>
    * </ul> */

  private void subGet() {
    try {
      // Get the AIDs to fetch info for
      System.out.println("Type a comma-delimited list of accounts to get data for");
      String[] aid = prompt("ACCT > AID").split(",");

      // Fetch information for each AID individually
      for(String a : aid) {
        String out = "";
        int res = 0;
        a = a.trim();

        // AID, Type, Open/Close, Balance
        ps = con.genQuery(
          "SELECT \"AID\", \"Type\", \"Date_Open\", \"Date_Close\", \"Balance\""+
          " FROM \"Account\" WHERE \"AID\" = "+a
        );
        rs = ps.executeQuery();
        while(rs.next()) { res++; out = "+ "+getAccount(); }
        rs.close();
        ps.close();

        if (res == 0) {
          System.out.println("!! Account "+a+" doesn't exist");
          continue;
        }

        // Recent card Num, PIN, CSC, Expiry
        if (!out.contains("SAV")) {
          ps = con.genQuery(
            "SELECT \"Number\", \"PIN\", \"Sec_Code\", \"Exp_Date\" FROM \"Card\""+
            " WHERE \"AID\" = "+a+" ORDER BY \"Exp_Date\" DESC"
          );
          rs = ps.executeQuery();
          rs.next();
          out += "\n  CRD: "+getCard();
          //while(rs.next()) { out += "\n  REC CRD: "+getCard(); }
          rs.close();
          ps.close();
        }
        
        // Owning customer CID, Name, and Phone
        out += "\n  OWN:";
        ps = con.genQuery(
          "SELECT \"CID\", \"Fname\", \"Lname\" FROM \"Customer\" WHERE "+
          "\"CID\" IN (SELECT \"CID\" FROM \"Account_Owner\" WHERE \"AID\""+
          "="+a+")"
        );
        rs = ps.executeQuery();
        while(rs.next()) {
          out += "\n    "+String.format(
            "(%4d) %s %s", rs.getInt("CID"), rs.getString("Fname"), rs.getString("Lname")
          );
        }
        rs.close();
        ps.close();

        System.out.println(out);
      }
    }
    catch(Exception e) {
      System.out.println("Input rejected: "+e.getMessage());
    }
  }

  /** getCard() reads a card's information from the ResultSet property of this
    * command. It modifies the expiration date by 3 years since the dates stored
    * are the dates the cards were assigned to the account. It returns a String
    * with basic information about the card. */

  private String getCard() throws SQLException {
    return String.format(
      "%s, PIN %s, SEC %s, expires %s", rs.getString("Number"), rs.getString("PIN"),
      rs.getString("Sec_Code"), rs.getDate("Exp_Date").toLocalDate().plusYears(3).toString()
    );
  }

  /** subSeek() allows the user to view information about an account or accounts
    * owned by a customer. It follows the below procedure:
    * <ul>
    *  <li>Get the customer ID that the user wishes to see account info for
    *      (which may be either numeric or a first, last name pair)
    *  <li>Prompt for clarification if multiple customers were returned
    *  <li>Show acount number, open/close date, type, and balance
    * </ul>
    *
    * A couple of errors can arise:
    * <ul>
    *  <li>An invalid customer ID is entered
    *  <li>The customer has no account
    * </ul> */

  private void subSeek() {
    try {
      CID cid = new CID();
      String id = "", tmp = "";

      // Get the customer's name or cid the user wishes to see info for
      System.out.println("What customer do you wish to see an account for?");
      if (!cid.add(prompt("ACCT > CID")))
        throw new Exception("Customer ID entered was invalid");

      // Get the ID of the customer and test if that customer exists
      if (cid.cntStr() == 1) {
        int results = 0;

        ps = con.genQuery(
          "SELECT \"CID\", \"SSN\", \"DOB\", \"Con_Phone\" FROM \"Customer\" WHERE "+
          "(\"Fname\",\"Lname\") IN "+cid.getInStr()
        );

        rs = ps.executeQuery();
        while(rs.next()) {
          results++;
          tmp +=
            String.format("(%4s) %s %s %s\n", rs.getString("CID"),
            rs.getString("SSN"), rs.getString("DOB"), rs.getString("Con_Phone"));
          id = rs.getString("CID");
        }
        rs.close();
        ps.close();

        // If multiple customers with the name exist, prompt for clarification
        if (results > 1) {
          System.out.println("Which of these customers (ID, SSN, DOB, Phone) is it?");
          System.out.print(tmp);
          id = prompt("ACCT > CID");
        }
        else if (results == 0)
          throw new Exception("No customer with that name exists");
      }
      else {
        // Remove '()' from the ID
        id = cid.getInNum();
        id = id.substring(1, id.length()-1);
      }

      // Get all account IDs for the customer
      ArrayList<String> aid = new ArrayList<>();
      ps = con.genQuery("SELECT \"AID\" FROM \"Account_Owner\" WHERE \"CID\"="+id);
      rs = ps.executeQuery();
      while(rs.next())
        aid.add(rs.getString("AID"));
      rs.close();
      ps.close();

      if (aid.size() == 0)
        throw new Exception("The customer owns no accounts presently");
      else {
        tmp = "(";
        for(String a : aid) { tmp += a + ", "; }
        tmp = tmp.substring(0,tmp.length()-2)+")";
      }
      
      // Get information for the account(s)
      ps = con.genQuery(
        "SELECT \"AID\", \"Date_Open\", \"Date_Close\", \"Balance\", \"Type\" FROM "+
        "\"Account\" WHERE \"AID\" IN "+tmp
      );
      rs = ps.executeQuery();
      System.out.println("These accounts are associated with the customer:");
      while(rs.next()) {
        System.out.println("+ "+getAccount());
      }
      rs.close();
      ps.close();
    }
    catch(SQLException e) {
      System.out.println("Query error: "+e.getMessage());
    }
    catch(Exception e) {
      System.out.println("Input rejected: "+e.getMessage());
    }
  }

  /** getAccount() gets basic account information from the ResultSet object of
    * this command, and returns it in a simplistic, String format. */

  public String getAccount() throws SQLException {
    return String.format(
      "%012d, %s: opened %s, closed %10s | Balance: $%.2f",
      rs.getLong("AID"), rs.getString("Type"), rs.getString("Date_Open"),
      rs.getString("Date_Close"), rs.getDouble("Balance")
    );
  }
}
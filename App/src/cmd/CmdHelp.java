package cmd;

public class CmdHelp extends Command {
  public void execute() {
    System.out.println(
      "Command List for DBMS Console Application\n"+
      "---------------------------------------------------------------------\n"+
      "customer   Perform actions related to customers\n"+
      "help       Displays this list of base commands\n"+
      "exit       Terminates the program's execution"
    );
  }
}
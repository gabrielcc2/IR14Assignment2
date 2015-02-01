/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ir.view;

import ir.control.WebCrawler;

import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;

/**
 * Secondary interface of the GUI, it consists of a window displaying unformatted text.
 * 
 * It is used both for status messages during the crawling process, as well as for displaying
 * lists of visited and excluded pages.
 * 
 * The visual aspects were developed with the help of NetBeans IDE for JSwing. However the code
 * required plenty of changes and adaptations.

 * @author Gabriel
 */
@SuppressWarnings("serial")
public class ListOfUnformattedTextDialog extends JDialog {
	boolean forCrawling=false;/**Allows to distinguish, when initializing, if the interface is for crawling messages
	or for printing excluded and visited lists.*/
	
    /**
     * Constructor, to be used for crawling messages. 
     * The input corresponds to the required data for crawling.
     * 
     */
	
    public ListOfUnformattedTextDialog(java.awt.Frame parent, boolean modal, List<URL> urls, Integer depth, String currentDir, boolean createIndex) {
        super(parent, modal);
        
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(ListOfUnformattedTextDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(ListOfUnformattedTextDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(ListOfUnformattedTextDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(ListOfUnformattedTextDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>
        //</editor-fold>
        //</editor-fold>
        //</editor-fold>
        
        forCrawling=true;
        List<String> messages = new ArrayList<String>();
        messages.add("Crawling will begin shortly. It might take several minutes.");
        
        initComponents(messages, "Crawling the Web");
        
        /**Here we create an additional thread that will start the crawling.
         * This is done so that thread can write status messages on the interface thats
         * already being executed by this thread. 
         * If we didn't do that, then we would only get to see the messages after the execution.
         * 
         */
        SeparateThreadToCallTheCrawling myRunnable = new SeparateThreadToCallTheCrawling(urls, depth, currentDir, createIndex); 
        Thread t = new Thread(myRunnable);
        t.start();
    }

    /**
     * Constructor to be used for printing lists
     * 
     *  @param label element is the title of the window. 
     */
    public ListOfUnformattedTextDialog(java.awt.Frame parent, boolean modal, List<String> pages, String label) {
        super(parent, modal);
        
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(ListOfUnformattedTextDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(ListOfUnformattedTextDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(ListOfUnformattedTextDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(ListOfUnformattedTextDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>
        //</editor-fold>
        //</editor-fold>
        //</editor-fold>
        
        forCrawling=false;
        initComponents(pages, label); 
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
 
    // <editor-fold defaultstate="collapsed" desc="Generated Code">                          
    private void initComponents(List<String> pages, String label) {
    	/**We call the constructors of all the components...*/
        jLabel1 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTextPane1 = new javax.swing.JTextPane();
        crawlingStatusMessageBoard= new javax.swing.JTextArea();
        
        /**Now we start the configuration*/

        //General configuration

    	this.setTitle(label);
    	
    	this.addWindowListener(new WindowAdapter() {

    	        @Override
    	        public void windowClosing(WindowEvent arg0) {
    	        	if(forCrawling) {
    	        		if(WebCrawler.getInstance().isCrawling()){
    	        			System.out.println("The crawling process should not be stopped, for consistency reasons.");
    	        		}
    	        		else{
    	        			dispose();
    	        		}
    	        	}
    	        	else{
    	        		dispose();
    	        	}
    	        }
    	 });
    	

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);

        jLabel1.setText(label+": ");
        jTextPane1.setEditable(false);
        
        
        StyledDocument doc = jTextPane1.getStyledDocument();//Snippet only used in the print-list mode
        if (pages.size()>1){
        	for (int i=pages.size()-1; i>=0; i--){//We print backwards, cause in the StyledDocument we are always inserting at the beginning.
       			try {
       				doc.insertString(0, i+1+": "+pages.get(i)+"\n", null);
       			} catch (BadLocationException e) {
       			// TODO Auto-generated catch block
       			e.printStackTrace();
       			}
        	}
        	jTextPane1.setCaretPosition(0);
        }
        else{
        	try {
				doc.insertString(0, pages.get(0), null);
			} catch (BadLocationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }


        
        if(!forCrawling){

            jTextPane1.addMouseListener(new MouseAdapter() {  //Allowing to copy text...
                public void mouseReleased(final MouseEvent e) { 
                    if (e.isPopupTrigger() || (e.getModifiers() & InputEvent.BUTTON3_MASK) == InputEvent.BUTTON3_MASK
                    	){  
                    	final JTextComponent component = (JTextComponent)e.getComponent(); 
                        final JPopupMenu menu = new JPopupMenu();  
                        JMenuItem item;  
                        item = new JMenuItem(new DefaultEditorKit.CopyAction());  
                        item.setText("Copy");  
                        item.setEnabled(component.getSelectionStart() != component.getSelectionEnd());  
                        menu.add(item);  
                        menu.show(e.getComponent(), e.getX(), e.getY());  
                    }  
                }  
            }); 
            
        	jScrollPane2.setViewportView(jTextPane1);

        	/**Layout configuration*/
        	javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        	getContentPane().setLayout(layout);
        	layout.setHorizontalGroup(
        			layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        			.addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
        					.addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        					.addGroup(layout.createSequentialGroup()
        					.addComponent(jLabel1)
                    .addGap(0, 300, Short.MAX_VALUE))
                    .addComponent(jScrollPane2)
            );
            layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(4, 4, 4)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 284, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED))
            );
        }
        else{

            jLabel1.setText(label+": This process might take a few minutes.");
            
            crawlingStatusMessageBoard.addMouseListener(new MouseAdapter() {  //Allowing to copy text...
                public void mouseReleased(final MouseEvent e) { 
                    if (e.isPopupTrigger() || (e.getModifiers() & InputEvent.BUTTON3_MASK) == InputEvent.BUTTON3_MASK
                    	){  
                    	final JTextComponent component = (JTextComponent)e.getComponent(); 
                        final JPopupMenu menu = new JPopupMenu();  
                        JMenuItem item;  
                        item = new JMenuItem(new DefaultEditorKit.CopyAction());  
                        item.setText("Copy");  
                        item.setEnabled(component.getSelectionStart() != component.getSelectionEnd());  
                        menu.add(item);  
                        menu.show(e.getComponent(), e.getX(), e.getY());  
                    }  
                }  
            }); 
        	/**Setting the scroller to bottom when it updates.*/
        	crawlingStatusMessageBoard.setCaretPosition(crawlingStatusMessageBoard.getDocument().getLength());
        	((DefaultCaret) crawlingStatusMessageBoard.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        	jScrollPane2.setViewportView(crawlingStatusMessageBoard);

        	/**Layout configuration*/

        	javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        	getContentPane().setLayout(layout);
        	layout.setHorizontalGroup(
        			layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        			.addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
        					.addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        					.addGroup(layout.createSequentialGroup()
        					.addComponent(jLabel1)
                    .addGap(0, 300, Short.MAX_VALUE))
                    .addComponent(jScrollPane2)
            );
            layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(4, 4, 4)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 284, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED))
            );
        }
        if(forCrawling){
        	System.setOut(new PrintStream(new TextAreaOutputStream(crawlingStatusMessageBoard)));//We ask the System.out to be temporarily outputed to the text area
        }
        pack();
    }// </editor-fold>                        

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(ListOfUnformattedTextDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(ListOfUnformattedTextDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(ListOfUnformattedTextDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(ListOfUnformattedTextDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>
        //</editor-fold>
        //</editor-fold>
        //</editor-fold>

        /* Create and display the dialog */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
            	List<String> input = new ArrayList<String>();
            	input.add("No text to display.");
                ListOfUnformattedTextDialog dialog = new ListOfUnformattedTextDialog(new javax.swing.JFrame(), true, input, "Visited pages");
                dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        System.exit(0);
                    }
                });
                dialog.setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify                     
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTextPane jTextPane1;
    private javax.swing.JTextArea crawlingStatusMessageBoard;
    // End of variables declaration
    
    private class TextAreaOutputStream extends OutputStream {/**This allows us to output status messages
    by redirecting System.out into a textArea**/

        private final JTextArea textArea;

        private final StringBuilder sb = new StringBuilder();

        public TextAreaOutputStream(final JTextArea textArea) {
            this.textArea = textArea;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public void write(int b) throws IOException {

            if (b == '\r') {
                return;
            }

            if (b == '\n') {
                final String text = sb.toString() + "\n";
				textArea.append(text);
                sb.setLength(0);
            } else {
                sb.append((char) b);
            }
        }
     }
    private class SeparateThreadToCallTheCrawling implements Runnable {//Additional thread in charge of starting the crawling.
    	List<URL> urls= new ArrayList<URL>();
    	int depth;
    	String currentDir;
    	boolean createIndex;

        public SeparateThreadToCallTheCrawling(List<URL> urls, int depth, String currentDir, boolean createIndex) {
        	this.urls.clear();
        	this.urls.addAll(urls);
        	this.depth=depth;
        	this.currentDir=currentDir;
        	this.createIndex=createIndex;
        }

        public void run() {
        	try {
				WebCrawler.getInstance().crawl(urls, depth, currentDir, createIndex);
	            jLabel1.setText("Crawling the Web: Done.");
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				jLabel1.setText("Crawling the Web: Finished.");
				e.printStackTrace();
			}
        }
    }
   }

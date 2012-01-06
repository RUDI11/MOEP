package MoepClient;

import Moep.Statusmeldung;
import MoepClient.netzwerk.ServerSuche;
import Moep.Karte;
import MoepClient.GUI.GUI;
import MoepClient.GUI.FarbeWuenschenDialog;
import MoepClient.GUI.Hand;
import MoepClient.GUI.InitPanel;
import MoepClient.GUI.SpielerDialog;
import java.awt.event.WindowEvent;
import MoepClient.netzwerk.Verbindung;
import java.applet.Applet;
import java.applet.AudioClip;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.image.CropImageFilter;
import java.awt.image.FilteredImageSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.event.PopupMenuListener;

/**
 * Die Interface-Klasse, der Knotenpunkt zwischen Netzwerk, GUI und Moep
 * @author Christian Diller & Philipp Herrle
 */

public class Interface
{

    private boolean dran;
    private boolean eingeloggt;
    
    private GUI g;
    private Moep m;
    private Verbindung verbindung;
    
    private Map<String, String> server;
    private Spielerverwaltung spieler;
    
    private ServerSuche serversuche;
    
    public Interface ()
    {
        server = new HashMap<String, String>();
        //<editor-fold defaultstate="collapsed" desc="Mouse-Adapter">
        MouseAdapter[] adapter = new MouseAdapter[] {
            
            new MouseAdapter() { //KarteZiehen
                @Override
                public void mousePressed(MouseEvent me){
                    zieheKarte();
                }
            },
            
            new MouseAdapter() { //KarteLegen
                @Override
                public void mousePressed(MouseEvent me) {
                    Hand hand = (Hand)me.getComponent();
                    int index = hand.gibIndexKarte(me.getPoint());
                    
                    if (index != -1){
                        karteLegen(index);
                    }
                }
            },
            
            new MouseAdapter() { //spielerDialog
                @Override
                public void mousePressed(final MouseEvent me) {
                    SpielerDialog spDialog = new SpielerDialog(spieler);
                    spDialog.addWindowListener(new WindowAdapter(){
                        @Override
                        public void windowClosing(WindowEvent e) {
                            spieler = ((SpielerDialog)e.getSource()).gibSpielerverwaltung();
                            g.setEnabled(true);
                    }});
                    g.setEnabled(false);
                }
            },
            
            new MouseAdapter() { //Erstellen
                @Override
                public void mousePressed(MouseEvent me) {
                    InitPanel ip = (InitPanel)me.getComponent().getParent();
                    if(ip.gibErstellenText() == "Erstellen") {
                        if(ip.gibErstellenServername() != "Servername" || ip.gibErstellenServername() != "") {
                            serverErstellen(ip.gibErstellenServername());
                            ip.statusErstellen(false, "Beenden");
                            ip.statusBeitreten(false, "Beitreten");
                        }
                        else
                            Statusmeldung.fehlerAnzeigen("Bitte erst einen Servernamen eingeben");
                    }
                    else
                    {
                        verbindung.serverBeenden();
                        spielEnde(false);
                        ip.statusErstellen(true, "Erstellen");
                        ip.statusBeitreten(true, "Beitreten");
                    }
                }
            },
            
            new MouseAdapter() { //Beitreten
                @Override
                public void mousePressed(MouseEvent me) {
                    InitPanel ip = (InitPanel)me.getComponent().getParent();
                    if(ip.gibBeitretenText() == "Beitreten") {
                        if(ip.gibName() != "" || ip.gibName() != "Spielername") {
                            verbindung = new Verbindung(server.get(ip.gibServername()), Interface.this);
                            verbindung.anmelden(ip.gibName());               
                            ip.statusErstellen(false, "-");
                            ip.statusBeitreten(false, "Verlassen");
                        }
                        else
                            Statusmeldung.fehlerAnzeigen("Bitte erst einen Spielernamen eingeben");  
                    }
                    else
                    {
                        verbindung.schliessen();
                        spielEnde(false);
                        ip.statusErstellen(true, "Erstellen");
                        ip.statusBeitreten(true, "Beitreten");
                    }

                }
            },
            
            new MouseAdapter (){ //MoepButton
                @Override
                public void mousePressed(MouseEvent e) {
                    if(dran)
                    {
                        verbindung.sendeMoepButton();
                        playSound("moep");
                    }
                    else
                        playSound("beep");
                }
            }                
        };
        //</editor-fold>
        
        PopupMenuListener popupListener = new PopupMenuListener() {
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent evt) { }
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent evt) {
                Interface.this.serverSuchen();
            }
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent evt) { }
        };
        
        dran = false;
        eingeloggt = false;
      
        m = new Moep();        
        
        g = new GUI(this, adapter, popupListener);
        
        Statusmeldung.infoAnzeigen("Willkommen bei MOEP!");
        
        serversuche = new ServerSuche(Interface.this);
        serverSuchen();
    }
    
    
    //<editor-fold defaultstate="collapsed" desc="Karten: Ziehen, Legen, Empfangen">
    private void zieheKarte (){
        if (!dran){
            playSound("beep");
        }
        else{
            playSound("click");
            verbindung.sendeKarteZiehen();
            g.handAktualisieren(m.gibHand());
        }
    }
    
    private void karteLegen (int index){
        if (!dran){
            playSound("beep");
            return;
        }
        m.zuLegen(index);
        if (!verbindung.sendeKarteLegen(m.gibKarteAt(index))){
            Statusmeldung.fehlerAnzeigen("Karte konnte nicht gesendet werden");
        }
        else{
            playSound("click");
        }
    }
    
    public void karteEmpfangen (Karte karte){
        m.ziehen(karte);
        g.handAktualisieren(m.gibHand());
    }
    
    public void ablageAkt (Karte k){
        g.ablageAktualisieren(k);
    }
    //</editor-fold>
    
     
    //<editor-fold defaultstate="collapsed" desc="Serversteuerung: zugLegal, dranSetzen, status, kick">
    public void zugLegal(boolean legal, int illegalArt)
    {
        if (legal)
        {
            m.legen();
            g.handAktualisieren(m.gibHand());
        }
        else
        {
            playSound("beep");  
            if(illegalArt == 1)
                m.legen();
                g.handAktualisieren(m.gibHand());
        }

    }
    
    public void dranSetzen(boolean dranH)
    {
        dran = dranH;
    }
    
    public void status (String statusH)
    {
        m.addNachricht(statusH);
        g.setStatus(m.gibStatus());
    }
    
    public void kick(String Grund)
    {
        if(eingeloggt)
        {
            Statusmeldung.warnungAnzeigen("Vom Server gekickt: " + Grund);
            logout();               
        }

    }
    //</editor-fold>
   
    
    //<editor-fold defaultstate="collapsed" desc="FarbeWuenschen">
    public void farbeWuenschenAnfrage()
    {
        FarbeWuenschenDialog farbeWuenschenDialog = new FarbeWuenschenDialog(this);
        g.setEnabled(false);
    }
    
    public void sendeFarbeWuenschenAntwort(int farbe)
    {
        verbindung.sendeFarbeWuenschenAntwort(farbe);
        g.setEnabled(true);
    }
    //</editor-fold>
    
        
    //<editor-fold defaultstate="collapsed" desc="Logout-Dialog">
    public boolean yesNoDialog()
    {
        ImageIcon icon = new ImageIcon(this.getClass().getResource("grafik/dialogSet.png"));
        icon.setImage(new JPanel().createImage(new FilteredImageSource(icon.getImage().getSource(), new CropImageFilter(1 * 32, 0 * 32, 32, 32))));
        
        return JOptionPane.showConfirmDialog(
                g,
                "Wirklich aus dem Spiel ausloggen?",
                "Logout", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                icon
                ) == JOptionPane.YES_OPTION;
    }
    //</editor-fold>

    
    //<editor-fold defaultstate="collapsed" desc="Mitspieler-Aktionen">
    public void mitspielerLogin(String name)
    {
        m.mitspielerLogin(name);
        g.setSpielStatus(m.gibSpielerliste());
    }
    
    public void mitspielerLogout(String name)
    {
        m.mitspielerLogout(name);
        g.setSpielStatus(m.gibSpielerliste());
    }
    
    public void mitspielerAmZug(String spielername) {
        m.mitspielerAmZug(spielername);
        g.setSpielStatus(m.gibSpielerliste());
    }
    
    public void spielerKartenzahlUpdate(String spielername, int kartenzahl) {
        m.mitspielerKartenzahlUpdate(spielername, kartenzahl);
    }
    //</editor-fold>
    

    //<editor-fold defaultstate="collapsed" desc="Spielende: reset, logout, beenden">
    public void spielEnde(boolean gewonnen)
    {
        m.setzeHand(new ArrayList<Karte>());
        g.handAktualisieren(m.gibHand());
        g.ablageReset();
        if(gewonnen)
            playSound("applause");
        else
            playSound("ooh");
    }
    
    private void logout()
    {
        eingeloggt = false;
        dran = false;
        verbindung.schliessen();
        spielEnde(false);
        m.statusReset();
        g.setSpielStatus(m.gibSpielerliste());
        g.setStatus(m.gibStatus());
    }
    
    public void beenden()
    {
        g.dispose();
        System.exit(0);
    }
    //</editor-fold>

    
    public void verbindungVerloren() 
    {
        if(eingeloggt)
        {
            Statusmeldung.fehlerAnzeigen("Verbindung verloren :(");
            logout();
        }
    }
       
    private void playSound(String soundName)
    {
        AudioClip clip = Applet.newAudioClip(this.getClass().getResource("sound/" + soundName + ".au"));
        clip.play();
    }

    public void serverGefunden(String serverName, String serverAdresse)
    {
        if(server.get(serverName) == null) {
            server.put(serverName, serverAdresse);
            g.serverGefunden(serverName);
        }
        else
            g.serverGefunden(null);
    }

    public void serverErstellen(String servername) 
    {
        if(serversuche.istEinzigerServer()){
            if(spieler.istGueltig())
            {
                verbindung = new Verbindung(spieler, servername, Interface.this);
                verbindung.anmelden(spieler.gibEigenenNamen());
            }
        }
        else
        {
            Statusmeldung.fehlerAnzeigen("Auf diesem PC läuft bereits ein Server");
        }
    }

    private void serverSuchen() 
    {
        serversuche.suchen();
        Statusmeldung.infoAnzeigen("Serverliste wurde aktualisiert");
    }
}

package net.sf.hajuergens.mydsl;

import net.sf.hajuergens.mydsl.MyDslStandaloneSetupGenerated;

/**
 * Initialization support for running Xtext languages without Equinox extension registry.
 */
@SuppressWarnings("all")
public class MyDslStandaloneSetup extends MyDslStandaloneSetupGenerated {
  public static void doSetup() {
    MyDslStandaloneSetup _myDslStandaloneSetup = new MyDslStandaloneSetup();
    _myDslStandaloneSetup.createInjectorAndDoEMFRegistration();
  }
}

package net.sf.hajuergens.mydsl.idea.parser.antlr.internal;

import org.eclipse.xtext.idea.parser.AbstractPsiAntlrParser;
import net.sf.hajuergens.mydsl.idea.lang.MyDslElementTypeProvider;
import org.eclipse.xtext.idea.parser.TokenTypeProvider;
import org.eclipse.xtext.parser.antlr.XtextTokenStream;
import org.eclipse.xtext.parser.antlr.XtextTokenStream.HiddenTokens;
import net.sf.hajuergens.mydsl.services.MyDslGrammarAccess;

import com.intellij.lang.PsiBuilder;


import org.antlr.runtime.*;
import java.util.Stack;
import java.util.List;
import java.util.ArrayList;

@SuppressWarnings("all")
public class PsiInternalMyDslParser extends AbstractPsiAntlrParser {
    public static final String[] tokenNames = new String[] {
        "<invalid>", "<EOR>", "<DOWN>", "<UP>", "RULE_ID", "RULE_INT", "RULE_STRING", "RULE_ML_COMMENT", "RULE_SL_COMMENT", "RULE_WS", "RULE_ANY_OTHER", "'datatype'", "'entity'", "'extends'", "'{'", "'}'", "'many'", "':'"
    };
    public static final int RULE_ID=4;
    public static final int RULE_WS=9;
    public static final int RULE_STRING=6;
    public static final int RULE_ANY_OTHER=10;
    public static final int RULE_SL_COMMENT=8;
    public static final int T__15=15;
    public static final int T__16=16;
    public static final int T__17=17;
    public static final int RULE_INT=5;
    public static final int T__11=11;
    public static final int RULE_ML_COMMENT=7;
    public static final int T__12=12;
    public static final int T__13=13;
    public static final int T__14=14;
    public static final int EOF=-1;

    // delegates
    // delegators


        public PsiInternalMyDslParser(TokenStream input) {
            this(input, new RecognizerSharedState());
        }
        public PsiInternalMyDslParser(TokenStream input, RecognizerSharedState state) {
            super(input, state);
             
        }
        

    public String[] getTokenNames() { return PsiInternalMyDslParser.tokenNames; }
    public String getGrammarFileName() { return "PsiInternalMyDsl.g"; }



    	protected MyDslGrammarAccess grammarAccess;

    	protected MyDslElementTypeProvider elementTypeProvider;

    	public PsiInternalMyDslParser(PsiBuilder builder, TokenStream input, MyDslElementTypeProvider elementTypeProvider, MyDslGrammarAccess grammarAccess) {
    		this(input);
    		setPsiBuilder(builder);
        	this.grammarAccess = grammarAccess;
    		this.elementTypeProvider = elementTypeProvider;
    	}

    	@Override
    	protected String getFirstRuleName() {
    		return "MyDsl";
    	}




    // $ANTLR start "entryRuleMyDsl"
    // PsiInternalMyDsl.g:52:1: entryRuleMyDsl returns [Boolean current=false] : iv_ruleMyDsl= ruleMyDsl EOF ;
    public final Boolean entryRuleMyDsl() throws RecognitionException {
        Boolean current = false;

        Boolean iv_ruleMyDsl = null;


        try {
            // PsiInternalMyDsl.g:52:47: (iv_ruleMyDsl= ruleMyDsl EOF )
            // PsiInternalMyDsl.g:53:2: iv_ruleMyDsl= ruleMyDsl EOF
            {
             markComposite(elementTypeProvider.getMyDslElementType()); 
            pushFollow(FOLLOW_1);
            iv_ruleMyDsl=ruleMyDsl();

            state._fsp--;

             current =iv_ruleMyDsl; 
            match(input,EOF,FOLLOW_2); 

            }

        }
        catch (RecognitionException re) {
            reportError(re);
            recover(input,re);
        }
        finally {
        }
        return current;
    }
    // $ANTLR end "entryRuleMyDsl"


    // $ANTLR start "ruleMyDsl"
    // PsiInternalMyDsl.g:59:1: ruleMyDsl returns [Boolean current=false] : ( (lv_elements_0_0= ruleType ) )* ;
    public final Boolean ruleMyDsl() throws RecognitionException {
        Boolean current = false;

        Boolean lv_elements_0_0 = null;


        try {
            // PsiInternalMyDsl.g:60:1: ( ( (lv_elements_0_0= ruleType ) )* )
            // PsiInternalMyDsl.g:61:2: ( (lv_elements_0_0= ruleType ) )*
            {
            // PsiInternalMyDsl.g:61:2: ( (lv_elements_0_0= ruleType ) )*
            loop1:
            do {
                int alt1=2;
                int LA1_0 = input.LA(1);

                if ( ((LA1_0>=11 && LA1_0<=12)) ) {
                    alt1=1;
                }


                switch (alt1) {
            	case 1 :
            	    // PsiInternalMyDsl.g:62:3: (lv_elements_0_0= ruleType )
            	    {
            	    // PsiInternalMyDsl.g:62:3: (lv_elements_0_0= ruleType )
            	    // PsiInternalMyDsl.g:63:4: lv_elements_0_0= ruleType
            	    {

            	    				markComposite(elementTypeProvider.getMyDsl_ElementsTypeParserRuleCall_0ElementType());
            	    			
            	    pushFollow(FOLLOW_3);
            	    lv_elements_0_0=ruleType();

            	    state._fsp--;


            	    				doneComposite();
            	    				if(!current) {
            	    					associateWithSemanticElement();
            	    					current = true;
            	    				}
            	    			

            	    }


            	    }
            	    break;

            	default :
            	    break loop1;
                }
            } while (true);


            }

        }
        catch (RecognitionException re) {
            reportError(re);
            recover(input,re);
        }
        finally {
        }
        return current;
    }
    // $ANTLR end "ruleMyDsl"


    // $ANTLR start "entryRuleType"
    // PsiInternalMyDsl.g:79:1: entryRuleType returns [Boolean current=false] : iv_ruleType= ruleType EOF ;
    public final Boolean entryRuleType() throws RecognitionException {
        Boolean current = false;

        Boolean iv_ruleType = null;


        try {
            // PsiInternalMyDsl.g:79:46: (iv_ruleType= ruleType EOF )
            // PsiInternalMyDsl.g:80:2: iv_ruleType= ruleType EOF
            {
             markComposite(elementTypeProvider.getTypeElementType()); 
            pushFollow(FOLLOW_1);
            iv_ruleType=ruleType();

            state._fsp--;

             current =iv_ruleType; 
            match(input,EOF,FOLLOW_2); 

            }

        }
        catch (RecognitionException re) {
            reportError(re);
            recover(input,re);
        }
        finally {
        }
        return current;
    }
    // $ANTLR end "entryRuleType"


    // $ANTLR start "ruleType"
    // PsiInternalMyDsl.g:86:1: ruleType returns [Boolean current=false] : (this_DataType_0= ruleDataType | this_Entity_1= ruleEntity ) ;
    public final Boolean ruleType() throws RecognitionException {
        Boolean current = false;

        Boolean this_DataType_0 = null;

        Boolean this_Entity_1 = null;


        try {
            // PsiInternalMyDsl.g:87:1: ( (this_DataType_0= ruleDataType | this_Entity_1= ruleEntity ) )
            // PsiInternalMyDsl.g:88:2: (this_DataType_0= ruleDataType | this_Entity_1= ruleEntity )
            {
            // PsiInternalMyDsl.g:88:2: (this_DataType_0= ruleDataType | this_Entity_1= ruleEntity )
            int alt2=2;
            int LA2_0 = input.LA(1);

            if ( (LA2_0==11) ) {
                alt2=1;
            }
            else if ( (LA2_0==12) ) {
                alt2=2;
            }
            else {
                NoViableAltException nvae =
                    new NoViableAltException("", 2, 0, input);

                throw nvae;
            }
            switch (alt2) {
                case 1 :
                    // PsiInternalMyDsl.g:89:3: this_DataType_0= ruleDataType
                    {

                    			markComposite(elementTypeProvider.getType_DataTypeParserRuleCall_0ElementType());
                    		
                    pushFollow(FOLLOW_2);
                    this_DataType_0=ruleDataType();

                    state._fsp--;


                    			current = this_DataType_0;
                    			doneComposite();
                    		

                    }
                    break;
                case 2 :
                    // PsiInternalMyDsl.g:98:3: this_Entity_1= ruleEntity
                    {

                    			markComposite(elementTypeProvider.getType_EntityParserRuleCall_1ElementType());
                    		
                    pushFollow(FOLLOW_2);
                    this_Entity_1=ruleEntity();

                    state._fsp--;


                    			current = this_Entity_1;
                    			doneComposite();
                    		

                    }
                    break;

            }


            }

        }
        catch (RecognitionException re) {
            reportError(re);
            recover(input,re);
        }
        finally {
        }
        return current;
    }
    // $ANTLR end "ruleType"


    // $ANTLR start "entryRuleDataType"
    // PsiInternalMyDsl.g:110:1: entryRuleDataType returns [Boolean current=false] : iv_ruleDataType= ruleDataType EOF ;
    public final Boolean entryRuleDataType() throws RecognitionException {
        Boolean current = false;

        Boolean iv_ruleDataType = null;


        try {
            // PsiInternalMyDsl.g:110:50: (iv_ruleDataType= ruleDataType EOF )
            // PsiInternalMyDsl.g:111:2: iv_ruleDataType= ruleDataType EOF
            {
             markComposite(elementTypeProvider.getDataTypeElementType()); 
            pushFollow(FOLLOW_1);
            iv_ruleDataType=ruleDataType();

            state._fsp--;

             current =iv_ruleDataType; 
            match(input,EOF,FOLLOW_2); 

            }

        }
        catch (RecognitionException re) {
            reportError(re);
            recover(input,re);
        }
        finally {
        }
        return current;
    }
    // $ANTLR end "entryRuleDataType"


    // $ANTLR start "ruleDataType"
    // PsiInternalMyDsl.g:117:1: ruleDataType returns [Boolean current=false] : (otherlv_0= 'datatype' ( (lv_name_1_0= RULE_ID ) ) ) ;
    public final Boolean ruleDataType() throws RecognitionException {
        Boolean current = false;

        Token otherlv_0=null;
        Token lv_name_1_0=null;

        try {
            // PsiInternalMyDsl.g:118:1: ( (otherlv_0= 'datatype' ( (lv_name_1_0= RULE_ID ) ) ) )
            // PsiInternalMyDsl.g:119:2: (otherlv_0= 'datatype' ( (lv_name_1_0= RULE_ID ) ) )
            {
            // PsiInternalMyDsl.g:119:2: (otherlv_0= 'datatype' ( (lv_name_1_0= RULE_ID ) ) )
            // PsiInternalMyDsl.g:120:3: otherlv_0= 'datatype' ( (lv_name_1_0= RULE_ID ) )
            {

            			markLeaf(elementTypeProvider.getDataType_DatatypeKeyword_0ElementType());
            		
            otherlv_0=(Token)match(input,11,FOLLOW_4); 

            			doneLeaf(otherlv_0);
            		
            // PsiInternalMyDsl.g:127:3: ( (lv_name_1_0= RULE_ID ) )
            // PsiInternalMyDsl.g:128:4: (lv_name_1_0= RULE_ID )
            {
            // PsiInternalMyDsl.g:128:4: (lv_name_1_0= RULE_ID )
            // PsiInternalMyDsl.g:129:5: lv_name_1_0= RULE_ID
            {

            					markLeaf(elementTypeProvider.getDataType_NameIDTerminalRuleCall_1_0ElementType());
            				
            lv_name_1_0=(Token)match(input,RULE_ID,FOLLOW_2); 

            					if(!current) {
            						associateWithSemanticElement();
            						current = true;
            					}
            				

            					doneLeaf(lv_name_1_0);
            				

            }


            }


            }


            }

        }
        catch (RecognitionException re) {
            reportError(re);
            recover(input,re);
        }
        finally {
        }
        return current;
    }
    // $ANTLR end "ruleDataType"


    // $ANTLR start "entryRuleEntity"
    // PsiInternalMyDsl.g:148:1: entryRuleEntity returns [Boolean current=false] : iv_ruleEntity= ruleEntity EOF ;
    public final Boolean entryRuleEntity() throws RecognitionException {
        Boolean current = false;

        Boolean iv_ruleEntity = null;


        try {
            // PsiInternalMyDsl.g:148:48: (iv_ruleEntity= ruleEntity EOF )
            // PsiInternalMyDsl.g:149:2: iv_ruleEntity= ruleEntity EOF
            {
             markComposite(elementTypeProvider.getEntityElementType()); 
            pushFollow(FOLLOW_1);
            iv_ruleEntity=ruleEntity();

            state._fsp--;

             current =iv_ruleEntity; 
            match(input,EOF,FOLLOW_2); 

            }

        }
        catch (RecognitionException re) {
            reportError(re);
            recover(input,re);
        }
        finally {
        }
        return current;
    }
    // $ANTLR end "entryRuleEntity"


    // $ANTLR start "ruleEntity"
    // PsiInternalMyDsl.g:155:1: ruleEntity returns [Boolean current=false] : (otherlv_0= 'entity' ( (lv_name_1_0= RULE_ID ) ) (otherlv_2= 'extends' ( (otherlv_3= RULE_ID ) ) )? otherlv_4= '{' ( (lv_features_5_0= ruleFeature ) )* otherlv_6= '}' ) ;
    public final Boolean ruleEntity() throws RecognitionException {
        Boolean current = false;

        Token otherlv_0=null;
        Token lv_name_1_0=null;
        Token otherlv_2=null;
        Token otherlv_3=null;
        Token otherlv_4=null;
        Token otherlv_6=null;
        Boolean lv_features_5_0 = null;


        try {
            // PsiInternalMyDsl.g:156:1: ( (otherlv_0= 'entity' ( (lv_name_1_0= RULE_ID ) ) (otherlv_2= 'extends' ( (otherlv_3= RULE_ID ) ) )? otherlv_4= '{' ( (lv_features_5_0= ruleFeature ) )* otherlv_6= '}' ) )
            // PsiInternalMyDsl.g:157:2: (otherlv_0= 'entity' ( (lv_name_1_0= RULE_ID ) ) (otherlv_2= 'extends' ( (otherlv_3= RULE_ID ) ) )? otherlv_4= '{' ( (lv_features_5_0= ruleFeature ) )* otherlv_6= '}' )
            {
            // PsiInternalMyDsl.g:157:2: (otherlv_0= 'entity' ( (lv_name_1_0= RULE_ID ) ) (otherlv_2= 'extends' ( (otherlv_3= RULE_ID ) ) )? otherlv_4= '{' ( (lv_features_5_0= ruleFeature ) )* otherlv_6= '}' )
            // PsiInternalMyDsl.g:158:3: otherlv_0= 'entity' ( (lv_name_1_0= RULE_ID ) ) (otherlv_2= 'extends' ( (otherlv_3= RULE_ID ) ) )? otherlv_4= '{' ( (lv_features_5_0= ruleFeature ) )* otherlv_6= '}'
            {

            			markLeaf(elementTypeProvider.getEntity_EntityKeyword_0ElementType());
            		
            otherlv_0=(Token)match(input,12,FOLLOW_4); 

            			doneLeaf(otherlv_0);
            		
            // PsiInternalMyDsl.g:165:3: ( (lv_name_1_0= RULE_ID ) )
            // PsiInternalMyDsl.g:166:4: (lv_name_1_0= RULE_ID )
            {
            // PsiInternalMyDsl.g:166:4: (lv_name_1_0= RULE_ID )
            // PsiInternalMyDsl.g:167:5: lv_name_1_0= RULE_ID
            {

            					markLeaf(elementTypeProvider.getEntity_NameIDTerminalRuleCall_1_0ElementType());
            				
            lv_name_1_0=(Token)match(input,RULE_ID,FOLLOW_5); 

            					if(!current) {
            						associateWithSemanticElement();
            						current = true;
            					}
            				

            					doneLeaf(lv_name_1_0);
            				

            }


            }

            // PsiInternalMyDsl.g:182:3: (otherlv_2= 'extends' ( (otherlv_3= RULE_ID ) ) )?
            int alt3=2;
            int LA3_0 = input.LA(1);

            if ( (LA3_0==13) ) {
                alt3=1;
            }
            switch (alt3) {
                case 1 :
                    // PsiInternalMyDsl.g:183:4: otherlv_2= 'extends' ( (otherlv_3= RULE_ID ) )
                    {

                    				markLeaf(elementTypeProvider.getEntity_ExtendsKeyword_2_0ElementType());
                    			
                    otherlv_2=(Token)match(input,13,FOLLOW_4); 

                    				doneLeaf(otherlv_2);
                    			
                    // PsiInternalMyDsl.g:190:4: ( (otherlv_3= RULE_ID ) )
                    // PsiInternalMyDsl.g:191:5: (otherlv_3= RULE_ID )
                    {
                    // PsiInternalMyDsl.g:191:5: (otherlv_3= RULE_ID )
                    // PsiInternalMyDsl.g:192:6: otherlv_3= RULE_ID
                    {

                    						if (!current) {
                    							associateWithSemanticElement();
                    							current = true;
                    						}
                    					

                    						markLeaf(elementTypeProvider.getEntity_SuperTypeEntityCrossReference_2_1_0ElementType());
                    					
                    otherlv_3=(Token)match(input,RULE_ID,FOLLOW_6); 

                    						doneLeaf(otherlv_3);
                    					

                    }


                    }


                    }
                    break;

            }


            			markLeaf(elementTypeProvider.getEntity_LeftCurlyBracketKeyword_3ElementType());
            		
            otherlv_4=(Token)match(input,14,FOLLOW_7); 

            			doneLeaf(otherlv_4);
            		
            // PsiInternalMyDsl.g:215:3: ( (lv_features_5_0= ruleFeature ) )*
            loop4:
            do {
                int alt4=2;
                int LA4_0 = input.LA(1);

                if ( (LA4_0==RULE_ID||LA4_0==16) ) {
                    alt4=1;
                }


                switch (alt4) {
            	case 1 :
            	    // PsiInternalMyDsl.g:216:4: (lv_features_5_0= ruleFeature )
            	    {
            	    // PsiInternalMyDsl.g:216:4: (lv_features_5_0= ruleFeature )
            	    // PsiInternalMyDsl.g:217:5: lv_features_5_0= ruleFeature
            	    {

            	    					markComposite(elementTypeProvider.getEntity_FeaturesFeatureParserRuleCall_4_0ElementType());
            	    				
            	    pushFollow(FOLLOW_7);
            	    lv_features_5_0=ruleFeature();

            	    state._fsp--;


            	    					doneComposite();
            	    					if(!current) {
            	    						associateWithSemanticElement();
            	    						current = true;
            	    					}
            	    				

            	    }


            	    }
            	    break;

            	default :
            	    break loop4;
                }
            } while (true);


            			markLeaf(elementTypeProvider.getEntity_RightCurlyBracketKeyword_5ElementType());
            		
            otherlv_6=(Token)match(input,15,FOLLOW_2); 

            			doneLeaf(otherlv_6);
            		

            }


            }

        }
        catch (RecognitionException re) {
            reportError(re);
            recover(input,re);
        }
        finally {
        }
        return current;
    }
    // $ANTLR end "ruleEntity"


    // $ANTLR start "entryRuleFeature"
    // PsiInternalMyDsl.g:241:1: entryRuleFeature returns [Boolean current=false] : iv_ruleFeature= ruleFeature EOF ;
    public final Boolean entryRuleFeature() throws RecognitionException {
        Boolean current = false;

        Boolean iv_ruleFeature = null;


        try {
            // PsiInternalMyDsl.g:241:49: (iv_ruleFeature= ruleFeature EOF )
            // PsiInternalMyDsl.g:242:2: iv_ruleFeature= ruleFeature EOF
            {
             markComposite(elementTypeProvider.getFeatureElementType()); 
            pushFollow(FOLLOW_1);
            iv_ruleFeature=ruleFeature();

            state._fsp--;

             current =iv_ruleFeature; 
            match(input,EOF,FOLLOW_2); 

            }

        }
        catch (RecognitionException re) {
            reportError(re);
            recover(input,re);
        }
        finally {
        }
        return current;
    }
    // $ANTLR end "entryRuleFeature"


    // $ANTLR start "ruleFeature"
    // PsiInternalMyDsl.g:248:1: ruleFeature returns [Boolean current=false] : ( ( (lv_many_0_0= 'many' ) )? ( (lv_name_1_0= RULE_ID ) ) otherlv_2= ':' ( (otherlv_3= RULE_ID ) ) ) ;
    public final Boolean ruleFeature() throws RecognitionException {
        Boolean current = false;

        Token lv_many_0_0=null;
        Token lv_name_1_0=null;
        Token otherlv_2=null;
        Token otherlv_3=null;

        try {
            // PsiInternalMyDsl.g:249:1: ( ( ( (lv_many_0_0= 'many' ) )? ( (lv_name_1_0= RULE_ID ) ) otherlv_2= ':' ( (otherlv_3= RULE_ID ) ) ) )
            // PsiInternalMyDsl.g:250:2: ( ( (lv_many_0_0= 'many' ) )? ( (lv_name_1_0= RULE_ID ) ) otherlv_2= ':' ( (otherlv_3= RULE_ID ) ) )
            {
            // PsiInternalMyDsl.g:250:2: ( ( (lv_many_0_0= 'many' ) )? ( (lv_name_1_0= RULE_ID ) ) otherlv_2= ':' ( (otherlv_3= RULE_ID ) ) )
            // PsiInternalMyDsl.g:251:3: ( (lv_many_0_0= 'many' ) )? ( (lv_name_1_0= RULE_ID ) ) otherlv_2= ':' ( (otherlv_3= RULE_ID ) )
            {
            // PsiInternalMyDsl.g:251:3: ( (lv_many_0_0= 'many' ) )?
            int alt5=2;
            int LA5_0 = input.LA(1);

            if ( (LA5_0==16) ) {
                alt5=1;
            }
            switch (alt5) {
                case 1 :
                    // PsiInternalMyDsl.g:252:4: (lv_many_0_0= 'many' )
                    {
                    // PsiInternalMyDsl.g:252:4: (lv_many_0_0= 'many' )
                    // PsiInternalMyDsl.g:253:5: lv_many_0_0= 'many'
                    {

                    					markLeaf(elementTypeProvider.getFeature_ManyManyKeyword_0_0ElementType());
                    				
                    lv_many_0_0=(Token)match(input,16,FOLLOW_4); 

                    					doneLeaf(lv_many_0_0);
                    				

                    					if (!current) {
                    						associateWithSemanticElement();
                    						current = true;
                    					}
                    				

                    }


                    }
                    break;

            }

            // PsiInternalMyDsl.g:268:3: ( (lv_name_1_0= RULE_ID ) )
            // PsiInternalMyDsl.g:269:4: (lv_name_1_0= RULE_ID )
            {
            // PsiInternalMyDsl.g:269:4: (lv_name_1_0= RULE_ID )
            // PsiInternalMyDsl.g:270:5: lv_name_1_0= RULE_ID
            {

            					markLeaf(elementTypeProvider.getFeature_NameIDTerminalRuleCall_1_0ElementType());
            				
            lv_name_1_0=(Token)match(input,RULE_ID,FOLLOW_8); 

            					if(!current) {
            						associateWithSemanticElement();
            						current = true;
            					}
            				

            					doneLeaf(lv_name_1_0);
            				

            }


            }


            			markLeaf(elementTypeProvider.getFeature_ColonKeyword_2ElementType());
            		
            otherlv_2=(Token)match(input,17,FOLLOW_4); 

            			doneLeaf(otherlv_2);
            		
            // PsiInternalMyDsl.g:292:3: ( (otherlv_3= RULE_ID ) )
            // PsiInternalMyDsl.g:293:4: (otherlv_3= RULE_ID )
            {
            // PsiInternalMyDsl.g:293:4: (otherlv_3= RULE_ID )
            // PsiInternalMyDsl.g:294:5: otherlv_3= RULE_ID
            {

            					if (!current) {
            						associateWithSemanticElement();
            						current = true;
            					}
            				

            					markLeaf(elementTypeProvider.getFeature_TypeTypeCrossReference_3_0ElementType());
            				
            otherlv_3=(Token)match(input,RULE_ID,FOLLOW_2); 

            					doneLeaf(otherlv_3);
            				

            }


            }


            }


            }

        }
        catch (RecognitionException re) {
            reportError(re);
            recover(input,re);
        }
        finally {
        }
        return current;
    }
    // $ANTLR end "ruleFeature"

    // Delegated rules


 

    public static final BitSet FOLLOW_1 = new BitSet(new long[]{0x0000000000000000L});
    public static final BitSet FOLLOW_2 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_3 = new BitSet(new long[]{0x0000000000001802L});
    public static final BitSet FOLLOW_4 = new BitSet(new long[]{0x0000000000000010L});
    public static final BitSet FOLLOW_5 = new BitSet(new long[]{0x0000000000006000L});
    public static final BitSet FOLLOW_6 = new BitSet(new long[]{0x0000000000004000L});
    public static final BitSet FOLLOW_7 = new BitSet(new long[]{0x0000000000018010L});
    public static final BitSet FOLLOW_8 = new BitSet(new long[]{0x0000000000020000L});

}
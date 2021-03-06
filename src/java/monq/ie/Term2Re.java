/*+********************************************************************* 
This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software Foundation
Foundation, Inc., 59 Temple Place - Suite 330, Boston MA 02111-1307, USA.
************************************************************************/

package monq.ie;

import monq.jfa.*;
import monq.jfa.actions.*;

/**
 * <p>is a collection of static functions to support the translation of a
 * multi word terms into regular expression which matches that term as
 * well as orthographic variations.</p>
 *
 * <p>
 * @author (C) 2003 Harald Kirsch
 * @version $Revision: 1.22 $, $Date: 2005-07-08 13:01:26 $
 */
public class Term2Re {
  // multi word terms are considered to be composed of 1 or more words
  // separated by this regular expression. Note that \n is NOT
  // included. 
  /**
   * <p>first parameter passed to {@link #createConverter
   * createConverter()} when creating the default converter used by
   * {@link #convert convert()}.</p>
   */
  public static final String RE_SPLIT_WORD = "[ \t\\-_]+";

  /**
   * <p>2nd parameter passed to {@link #createConverter
   * createConverter()} when creating the default converter used by
   * {@link #convert convert()}.</p>
   */
  public static final String RE_SEP_WORD = "[ \\-_]*";

  /**
   * <p>3rd parameter passed to {@link #createConverter
   * createConverter()} when creating the default converter used by
   * {@link #convert convert()}.</p>
   */
  public static final String RE_TRAIL_CONTEXT = "[^A-Za-z0-9]";

  /**
   * <p>4th parameter passed to {@link #createConverter
   * createrConverter()} when creating the default converter used by
   * {@link #convert convert()}.</p>
   */
  public static final ReParser reParser 
    = ReClassicParser.factory.newReParser();

  // is an automaton to split a string into words and white space. It
  // is initialized in the static block below.
  private static DfaRun convert;

  // stop words are copied unchanged
  private static String stopWords =
    "ii|iii|iv|vi|it|up|of|and|the|to|or|with|due|in|other|as|by|without";

  // automaton to replace ae by a?e. Used within a word
  private static DfaRun aeAut;

  // automaton to check whether a word will be subject to
  // uppercase/lowercase conversion of first character
  private static Regexp ulFirstOk = new Regexp(".*[a-z].*");


  static {
    try {
      convert = createConverter(RE_SPLIT_WORD, RE_SEP_WORD, RE_TRAIL_CONTEXT,
				reParser);

//   	= new Nfa(wordSplitIn, new AbstractFaAction.Replace(wordSplitOut))
// 	.or("[A-Za-z]("+wordSplitIn+")^", new DoOrdinaryWord())
// 	.or("("+wordSplitIn+")^", new DoFunnyWord())
// 	.or(stopWords, AbstractFaAction.COPY)
// 	.compile()
// 	.createRun(DfaRun.UNMATCHED_THROW);

      aeAut
	= new Nfa(".ae", new AbstractFaAction() {
	    public void invoke(StringBuilder yytext, int start, DfaRun r) {
	      yytext.setLength(start+1);
	      yytext.append("a?e");
	    }
	  })
	.compile(DfaRun.UNMATCHED_COPY)
	.createRun();

    } catch( ReSyntaxException e ) {
      throw new Error("this cannot happen", e);
    } catch( CompileDfaException e ) {
      throw new Error("this cannot happen", e);
    }
  }
  /**********************************************************************/
  /**
   * <p>creates a {@link monq.jfa.DfaRun DfaRun} able to convert a multi word
   * term into a regular expression which matches obvious
   * orthographical variations of that term. You may then use the
   * machines filter functions to convert a multi word term to a
   * regular expression. The resulting regular expression will match
   * generalisations of the given term, e.g.</p>
   * <ul>
   * <li>capitalization of words</li>
   * <li>pluralization of words, including "ies" as the plural of "y"</li>
   * <li>matching of "e" alone where "ae" is in the mwt (anemia
   * vs. anaemia)</li>
   * </ul>
   * <p><b>Note:</b> This comment does not necessarily keep up with
   * changes to the code.-(</p>
   *
   * @param wordSplitRe is the regular expression used to separate an
   * incoming term into individual words. See {@link #RE_SPLIT_WORD} for
   * an example.
   *
   * @param wordSepRe is a regular expression put between the regular
   * expressions generated for individual words of the multi word
   * term.
   *
   * @param rp must be an {@link ReParser} of the type for which the
   * regular expression is generated. This is necessary to be able to
   * escape characters in original multi word term properly.
   *
   * @param trailContextRe is a regular expression which will be
   * finally appended to the result.
   */
  public static DfaRun createConverter(String wordSplitRe,
				       String wordSepRe, 
				       String trailContextRe,
				       ReParser rp) 
    throws ReSyntaxException {
    Dfa dfa;
    try {
      Nfa nfa = new 
	Nfa(wordSplitRe, new Replace(wordSepRe))
	.or("[A-Za-z]("+wordSplitRe+")^", new DoOrdinaryWord(rp))
	.or("("+wordSplitRe+")^", new DoFunnyWord(rp))
	.or(stopWords, Copy.COPY)
	;
      FaAction eofAction = null;
      if( trailContextRe!=null && trailContextRe.length()>0 ) {
	eofAction = new Replace(trailContextRe);
      }
      dfa = nfa.compile(DfaRun.UNMATCHED_THROW, eofAction);
    } catch( CompileDfaException e ) {
      throw new Error("impossible", e);
    }
    return new DfaRun(dfa);
  }
  /**********************************************************************/
  private static class DoFunnyWord extends AbstractFaAction {
    private ReParser rp;
    private StringBuilder scratch = new StringBuilder();
    private DoFunnyWord(ReParser rp) { 
      priority = -2; 
      this.rp = rp;
    }
    public void invoke(StringBuilder yytext, int start, DfaRun r) {
      scratch.setLength(0);
      rp.escape(scratch, yytext, start);
      yytext.setLength(start);
      yytext.append(scratch);
    }
  }
  /**********************************************************************/
  private static class DoOrdinaryWord extends AbstractFaAction {
    private ReParser rp;
    private StringBuilder scratch = new StringBuilder();
    private StringBuilder scratch2 = new StringBuilder();

    //public int getPriority() { return -1; }
    private DoOrdinaryWord(ReParser rp) { 
      this.rp = rp;
      priority = -1; 
    }
    public void invoke(StringBuilder yytext, int start, DfaRun r) {
      // transform characters with a special meaning in a regular
      // expression such that they stand only for themselves.
      scratch2.setLength(0);
      rp.escape(scratch2, yytext, start);


      scratch.setLength(0);
      aeAut.setIn(new CharSequenceCharSource(scratch2));
      try {
	aeAut.filter(scratch);
      } catch( java.io.IOException e) {
	throw new Error("this cannot happen: "+e);
      }

      int L = scratch.length();
      //System.out.println(scratch);

      if( ulFirstOk.matches(scratch) ) {
	// let first character match uppercase/lowercase
	char first = scratch.charAt(0);
	if( Character.isLetter(first) ) {
	  char lower = Character.toLowerCase(first);
	  char upper = Character.toUpperCase(first);
	  scratch.delete(0, 1);
	  scratch.insert(0, ']')
	    .insert(0, lower)
	    .insert(0, upper)
	    .insert(0, '[');
	}
      }

      // for a a word longer than 1 character, allow plural
      // trailing y is changed to (y|ies)
      // trailing s stays unchanged
      if( L>1 ) {
	int l = scratch.length();
	char last = scratch.charAt(l-1);
	if( last=='y' ) {
	  scratch.delete(l-1, l);
	  scratch.append("(y|ies)");
	} else if( last!='s' && last!='S' && Character.isLetter(last)) {
	  scratch.append("s?");
	}
      }
      yytext.setLength(start);
      yytext.append(scratch);
    }
  }

  
  private static StringBuilder sb = new StringBuilder(40);
  
  /**
   * <p>converts a multi word term into a regular expression matching
   * that term as well as obvious generalizations. The algorithm is
   * not finalized yet. The converter used is created with {@link
   * #createConverter} with {@link #RE_SEP_WORD}, {@link #RE_SPLIT_WORD}
   * and {@link #RE_TRAIL_CONTEXT} as parameters.</p>
   */
  public static synchronized String convert(String s) {
    sb.setLength(0);
    convert.setIn(new CharSequenceCharSource(s));
    try {
      convert.filter(sb);
    } catch( java.io.IOException e) {
      throw new Error("this cannot happen: "+e.toString(), e);
    }
    //sb.append("[^").append(letters).append(']');
    //sb.append("[^A-Za-z0-9]");
    return sb.toString();
  }
  /**********************************************************************/
  /**
   * <p>applies {@link #convert} to every line read from
   * <code>System.in</code> and writes the result to
   * <code>System.out</code>.</p> 
   */
  public static void main(String[] argv) throws java.io.IOException {
    //convert.getDfa().toDot(System.out);
    java.io.BufferedReader in = 
      new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
    String s;
    while( null!=(s=in.readLine()) ) {
      System.out.println(Misc.printable(convert(s)));
      //Misc.printable(convert(s));
    }
  }  
}

/*
 * Simple List Grammar
 * ==========================
*/

{
  function makeArray(start,step,end) {
    var result=[];
    for(var i = start; i <= end; i += step) {
    result.push(i);
    }
    return result;
  }
}

List = List3 / List2

List2
  = "(" _ start:Integer _ ":" _ end:Integer _ ")" {
  		return makeArray(start,1,end);
    }

List3 = '(' _ start:Integer _ ':' _ step:Integer _ ':' _ end:Integer _ ')' {
		return makeArray(start,step,end);
	}

Integer "integer"
  = digits:[0-9]+ { return parseInt(digits.join(""), 10); }

_ "whitespace"
  = [ \t\n\r]*
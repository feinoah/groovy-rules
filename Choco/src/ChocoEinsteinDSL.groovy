//@GrabResolver('http://www.emn.fr/z-info/choco-repo/mvn/repository')
//@Grab('choco:choco-solver:2.1.5')
import static choco.Choco.*
import choco.kernel.model.variables.integer.*
import groovy.transform.Field

enum Pet { dog, cat, bird, fish, horse }
enum Color { green, white, red, blue, yellow }
enum Sport { baseball, volleyball, football, hockey, tennis }
enum Drink { water, tea, milk, coffee, beer }
enum Nationality { Norwegian, Dane, Briton, German, Swede }

import static Pet.*
import static Color.*
import static Sport.*
import static Drink.*
import static Nationality.*

// define logic solver data structures
num = 5
center = 2
first = 0
println "Solving Einstein's Riddle:"

@Field m = new choco.cp.model.CPModel()
def s = new choco.cp.solver.CPSolver()
choco.Choco.metaClass.static.eq = { c, v -> delegate.eq(c, v.ordinal()) }
def makeEnumVar(st, arr) { choco.Choco.makeIntVar(st, 0, arr.size()-1, choco.Options.V_ENUM) }
pets    = new IntegerVariable[num]
colors  = new IntegerVariable[num]
plays   = new IntegerVariable[num]
drinks  = new IntegerVariable[num]
nations = new IntegerVariable[num]

(0..<num).each { i ->
     pets[i] = makeEnumVar("pet$i",    pets)
   colors[i] = makeEnumVar("color$i",  colors)
    plays[i] = makeEnumVar("plays$i",  plays)
   drinks[i] = makeEnumVar("drink$i",  drinks)
  nations[i] = makeEnumVar("nation$i", nations)
}

def pretty(s, c, arr, i) { c.values().find{ it.ordinal() == s.getVar(arr[i])?.value } }

// define DSL (simplistic non-refactored version)
def neighbours(var1, val1, var2, val2) {
  m.addConstraint and(
    ifOnlyIf(eq(var1[0], val1), eq(var2[1], val2)),
    implies(eq(var1[1], val1), or(eq(var2[0], val2), eq(var2[2], val2))),
    implies(eq(var1[2], val1), or(eq(var2[1], val2), eq(var2[3], val2))),
    implies(eq(var1[3], val1), or(eq(var2[2], val2), eq(var2[4], val2))),
    ifOnlyIf(eq(var1[4], val1), eq(var2[3], val2))
  )
}
iff = { e1, c1, e2, c2 -> m.addConstraint and(*(0..<num).collect{ ifOnlyIf(eq(e1[it], c1), eq(e2[it], c2)) }) }
isEq = { a, b -> m.addConstraint eq(a, b) }

dogs = dog; birds = bird; cats = cat; horses = horse
a = owner = house = the = abode = person = man = to = is = side = next = who = different = 'ignored'

// define the DSL in terms of DSL implementation
def the(Nationality n) {
  def ctx = [nations, n]
  [
    drinks:iff.curry(*ctx, drinks),
    plays:iff.curry(*ctx, plays),
    keeps:iff.curry(*ctx, pets),
    rears:iff.curry(*ctx, pets),
    owns:{ _the -> [first:{ house -> isEq(nations[first], n)}] },
    has:{ _a ->
      [pet:iff.curry(*ctx, pets)] + Color.values().collectEntries{ c ->
        [c.toString(), { _dummy -> iff(*ctx, colors, c) } ]
      }
    },
    lives: { _next -> [to: { _the ->
      Color.values().collectEntries{ c ->
        [c.toString(), { _dummy -> neighbours(*ctx, colors, c) } ]
      }
    }]}
  ]
}

def the(Color c1) {[
  house: { _is -> [on: { _the -> [left: { _side -> [of: { __the ->
    Color.values().collectEntries{ c2 -> [c2.toString(), { _dummy ->
      m.addConstraint and(*(1..<num).collect{ ifOnlyIf(eq(colors[it-1], c1), eq(colors[it], c2)) })
    }]}
  }]}]}]}
]}

def the(String _dummy) {[
  of:{ _the ->
    Color.values().collectEntries{ c -> [c.toString(), { _house -> [
      drinks:iff.curry(colors, c, drinks),
      plays:iff.curry(colors, c, plays)
    ] } ] }
  },
  known: { _to -> [
    play: { sport ->
      def ctx = [plays, sport]
      [
        rears: iff.curry(*ctx, pets),
        keeps: iff.curry(*ctx, pets),
        drinks: iff.curry(*ctx, drinks),
        lives: { _next -> [to: { _the -> [one: { _who -> [
          keeps: { pet -> neighbours(pets, pet, *ctx) },
          drinks: { beverage -> neighbours(drinks, beverage, *ctx) }
        ]}]}]}
      ]
    },
    keep : { pet -> [
      lives: { _next -> [to: { _the -> [man: { _who -> [
        plays: { sport -> neighbours(pets, pet, plays, sport) }
      ]}]}]}
    ]}
  ]},
  from: { _the -> [center: { house ->
    [drinks: { d -> isEq(drinks[center], d)}]
  }]}
]}

def all(IntegerVariable[] var) {
  [are: { _different -> m.addConstraint allDifferent(var) } ]
}

// define rules
all pets are different
all colors are different
all plays are different
all drinks are different
all nations are different
the man from the center house drinks milk
the Norwegian owns the first house
the Dane drinks tea
the German plays hockey
the Swede keeps dogs // alternate ending: has a pet dog
the Briton has a red house  // alternate ending: red abode
the owner of the green house drinks coffee
the owner of the yellow house plays baseball
the person known to play football rears birds // alternate ending: keeps birds
the man known to play tennis drinks beer
the green house is on the left side of the white house
the man known to play volleyball lives next to the one who keeps cats
the man known to keep horses lives next to the man who plays baseball
the man known to play volleyball lives next to the one who drinks water
the Norwegian lives next to the blue house

// invoke logic solver
s.read(m)
def more = s.solve()
while (more) {
  for (i in 0..<num) {
    print   'The ' + pretty(s, Nationality, nations, i)
    print   ' has a pet ' + pretty(s, Pet, pets, i)
    print   ' plays ' + pretty(s, Sport, plays, i)
    print   ' drinks ' + pretty(s, Drink, drinks, i)
    println ' and lives in a ' + pretty(s, Color, colors, i) + ' house'
  }
  more = s.nextSolution()
}

Having just gotten my code to work 13 hours after the due time. I'm slightly tempted to simply write a string of elated explitives.

However, in the spirit of proffesionalism, I will instead note that I had 2 major problems. The first was wrestling with some of the badly designed or otherwise frustrating elements of how java is designed. I lost about a day over the weekend trying to compile the regex parser and failing because I hadn't realized that I needed to make every function static in order to compile. Similarly, I had trouble getting my function to find all states reachable via 0 or more epsilon transitions from a set of starting states due to the fact that I was accidentally assigning the HashSets I was using to each other, leading the program to conclude no states were reachable because adding an element to one added it to the other. I fixed this by using an iterator to add elements one by one in lieu of assignment. Finally, I suffered significantly due to having inconsistent procedures for advancing the token. I eventually fixed it by writing out a clear that all functions assume that they with token set to their own token and will end by advancing to just outside their own boundaries.
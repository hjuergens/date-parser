module Test
        where
x = 5
y = (6, "Hello")
z = x * fst y

fibonacci 1 = 1
fibonacci 2 = 2
fibonacci n = fibonacci (n-1) + fibonacci (n-2)

my_map f [] = []
my_map f (x:xs) = f x : my_map f xs
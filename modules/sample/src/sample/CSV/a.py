a = ["\overline{A}","A"]
b = ["\overline{B}","B"]
c = ["\overline{C}","C"]
d = ["\overline{D}","D"]
e = ["\overline{E}","E"]
f = ["\overline{F}","F"]

for i in a:
    for j in b:
        for k in c:
            for l in d:
                for m in e:
                    for n in f:
                        print(i,j,k,l,m,n,"&  1  & -0.2  & -0.2  & -0.2  & -0.2  & -0.2 " , " \/"," \/")
                        print("\hline")

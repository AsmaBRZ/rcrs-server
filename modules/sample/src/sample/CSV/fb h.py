import matplotlib.pyplot as plt
import numpy as np
import os
time=np.arange(1,301)
array=np.zeros(250)
a=[]

fichiers=os.listdir("d")

for f in fichiers:
    print(f)
    i=0
    with open("d/"+f, "r") as ins:
        for line in ins:
            if i<300:
                print(line)
                l=line.split(" ")
                print(int(l[1]))
                print(i)
                print('jjjjjjjj')
                print(array[i])
                array[i]=array[i]+int(l[1])
                i=i+1

print (array)


plt.plot(array)
plt.ylabel("Nombre de voisins aidés")
plt.xlabel('Temps')

plt.suptitle('Agent random')
plt.show()
